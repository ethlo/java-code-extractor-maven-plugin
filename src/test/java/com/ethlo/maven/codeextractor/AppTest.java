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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * Unit test for simple App.
 */
public class AppTest
{
    @Test
    public void smokeTest() throws IllegalAccessException, IOException
    {
        final MavenProject project = new MavenProject();

        final ExtractMojo mojo = new ExtractMojo();
        FieldUtils.writeField(mojo, "sources", new String[]{"src/test/java/samples"}, true);
        FieldUtils.writeField(mojo, "template", "src/test/resources/sample-renderer.html", true);
        FieldUtils.writeField(mojo, "project", project, true);
        try
        {
            mojo.execute();
            System.out.println(project.getProperties().getProperty("src/test/java/samples"));
        }
        catch (MojoFailureException expected)
        {

        }
    }
}
