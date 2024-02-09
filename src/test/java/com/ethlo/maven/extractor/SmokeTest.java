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

import java.io.File;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class SmokeTest
{
    @Test
    public void smokeTest() throws IllegalAccessException, MojoFailureException
    {
        final MavenProject project = new MavenProject()
        {
            @Override
            public File getBasedir()
            {
                return new File("");
            }
        };

        final ExtractJavaMojo mojo = new ExtractJavaMojo();
        FieldUtils.writeField(mojo, "sources", new String[]{"src/test/java/samples"}, true);
        FieldUtils.writeField(mojo, "template", "src/test/resources/github.template.md", true);
        FieldUtils.writeField(mojo, "project", project, true);
        mojo.execute();
    }
}
