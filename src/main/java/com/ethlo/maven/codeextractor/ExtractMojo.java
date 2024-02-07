package com.ethlo.maven.codeextractor;

/*-
 * #%L
 * java-code-extractor-maven-plugin
 * %%
 * Copyright (C) 2024 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;

@Mojo(threadSafe = true, name = "extract", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ExtractMojo extends AbstractMojo
{
    @Parameter(required = true, property = "sources")
    private String[] sources;

    @Parameter(required = true, property = "template")
    private String template;

    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private MojoExecution mojoExecution;

    private PebbleTemplate compiledTemplate;

    @Override
    public void execute() throws MojoFailureException
    {
        if (skip)
        {
            return;
        }

        final PebbleEngine engine = new PebbleEngine.Builder().strictVariables(false).build();
        this.compiledTemplate = Objects.requireNonNull(engine.getTemplate(template), "Template was null");

        for (String source : sources)
        {
            getLog().info("Processing source '" + source + "'");
            final SourceRoot sourceRoot = new SourceRoot(project.getBasedir().toPath().resolve(source));
            try (final Stream<Path> files = Files.list(sourceRoot.getRoot()))
            {
                files
                        .filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".java"))
                        .forEach(javaFile -> sourceRoot.add(sourceRoot.parse("", javaFile.toString())));
            }
            catch (IOException exc)
            {
                throw new MojoFailureException(exc);
            }

            final List<MethodDeclaration> methods = sourceRoot.getCompilationUnits()
                    .stream()
                    .flatMap(cu -> visitCu(cu).stream())
                    .toList();
            final StringWriter sw = new StringWriter();
            try
            {
                getLog().info("Found " + methods.size() + " methods");
                compiledTemplate.evaluate(sw, Map.of("methods", process(methods)));
                project.getProperties().setProperty(source, sw.toString());
            }
            catch (IOException e)
            {
                throw new MojoFailureException(e);
            }
        }
    }

    private List<ExampleInfo> process(List<MethodDeclaration> methods)
    {
        return methods.stream().map(this::processMethod).toList();
    }

    private ExampleInfo processMethod(MethodDeclaration methodDeclaration)
    {
        final String description = methodDeclaration.getComment()
                .map(Comment::getContent)
                .map(StringUtils::normalizeSpace)
                .orElse(null);

        final String body = methodDeclaration.getBody()
                .map(BlockStmt::getStatements)
                .map(statements -> String.join("\n", statements.stream().map(Node::toString).collect(Collectors.joining("\n"))))
                .orElse(null);

        return new ExampleInfo(methodDeclaration.getName().asString(), description, body);
    }

    private List<MethodDeclaration> visitCu(CompilationUnit cu)
    {
        final List<MethodDeclaration> declarations = new ArrayList<>();
        cu.accept(new ModifierVisitor<Void>()
        {
            @Override
            public Visitable visit(MethodDeclaration n, Void arg)
            {
                declarations.add(n);
                return n;
            }
        }, null);
        return declarations;
    }

    public record ExampleInfo(String name, String description, String body)
    {

    }
}
