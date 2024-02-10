package com.ethlo.maven.extractor;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Problem;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Extractor for Java source files
 */
@Mojo(threadSafe = true, name = "extract", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class ExtractJavaMojo extends AbstractMojo
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
            final ParserConfiguration config = new ParserConfiguration();
            config.setLexicalPreservationEnabled(true);

            getLog().info("Processing source '" + source + "' using template " + template);
            final SourceRoot sourceRoot = new SourceRoot(project.getBasedir().toPath().resolve(source), config);
            sourceRoot.setPrinter(LexicalPreservingPrinter::print);
            List<CompilationUnit> compilationUnits;
            try
            {
                compilationUnits = sourceRoot.tryToParse().stream()
                        .map(pr ->
                        {
                            if (pr.isSuccessful())
                            {
                                return pr.getResult().orElse(null);
                            }
                            else
                            {
                                getLog().warn("Parse problem for " + source + ": "
                                        + pr.getProblems().stream().map(Problem::toString).collect(Collectors.joining(", ")));
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            catch (IOException e)
            {
                throw new MojoFailureException(e);
            }

            final List<MethodDeclaration> methods = compilationUnits
                    .stream()
                    .flatMap(cu -> visitCu(cu).stream())
                    .collect(Collectors.toList());

            if (!methods.isEmpty())
            {
                final StringWriter sw = new StringWriter();

                final TypeDeclaration<?> typeDeclaration = (TypeDeclaration<?>) methods.get(0).getParentNode().orElseThrow(() -> new IllegalArgumentException("Found no parent for method"));
                final String className = typeDeclaration.getName().asString();

                try
                {
                    getLog().info("Found " + methods.size() + " methods");
                    final Map<String, Object> model = new HashMap<>();
                    model.put("class", new ClassInfo(className, cleanComment(typeDeclaration.getComment().orElse(null)), source));
                    model.put("methods", methods.stream().map(this::processMethod).collect(Collectors.toList()));
                    compiledTemplate.evaluate(sw, model);
                    project.getProperties().setProperty(source, sw.toString());
                    if (getLog().isDebugEnabled())
                    {
                        getLog().debug("Render result for " + source + ": " + sw);
                    }
                }
                catch (IOException e)
                {
                    throw new MojoFailureException(e);
                }
            }
            else
            {
                getLog().warn("No methods found in " + source);
            }
        }
    }

    private MethodInfo processMethod(MethodDeclaration methodDeclaration)
    {
        final String description = cleanComment(methodDeclaration.getComment().orElse(null));
        final String body = getBody(methodDeclaration);
        return new MethodInfo(methodDeclaration.getName().asString(), description, body, getRange(methodDeclaration));
    }

    private Range getRange(MethodDeclaration methodDeclaration)
    {
        Position start = methodDeclaration.getRange().orElseThrow(() -> new IllegalArgumentException("No range info for method")).begin;
        if (methodDeclaration.getComment().isPresent())
        {
            start = methodDeclaration.getComment().get().getRange().orElseThrow(() -> new IllegalArgumentException("No range info for comment")).begin;
        }
        return new Range(start, methodDeclaration.getRange().get().end);
    }

    private static String getBody(MethodDeclaration methodDeclaration)
    {
        return methodDeclaration.getBody()
                .map(BlockStmt::getStatements)
                .map(statements -> statements.stream()
                        .map(LexicalPreservingPrinter::print)
                        .collect(Collectors.joining("\n")))
                .orElse(null);
    }

    private static String cleanComment(Comment comment)
    {
        final String nl = "[_NL_]";
        return Optional.ofNullable(comment)
                .map(Comment::getContent)
                .map(s -> s.replace("\n", nl))
                .map(StringUtils::normalizeSpace)
                .map(s -> s.replace(nl, "\n"))
                .orElse(null);
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

    /**
     * Holder of data for methods
     */
    public static class MethodInfo
    {
        private final String name;
        private final String description;
        private final String body;
        private final Range range;

        public MethodInfo(String name, String description, String body, Range range)
        {
            this.name = name;
            this.description = description;
            this.body = body;
            this.range = range;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getBody()
        {
            return body;
        }

        public Range getRange()
        {
            return range;
        }
    }

    /**
     * Holder of data for the class
     */
    public static class ClassInfo
    {
        private final String name;
        private final String description;
        private final String path;

        public ClassInfo(String name, String description, String path)
        {
            this.name = name;
            this.description = description;
            this.path = path;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public String getPath()
        {
            return path;
        }
    }
}
