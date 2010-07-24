/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.tools.stubgenerator

import static groovy.io.FileType.*

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit

import com.thoughtworks.qdox.JavaDocBuilder
import com.thoughtworks.qdox.model.JavaClass

/**
 * Base class for all the stub generator test samples.
 * <br><br>
 *
 * If you want to create a new test, you have to create a class extending <code>BaseStubTest</code>.
 * Your subclass has to implement <code>void verifyStubs()</code>.
 * <br><br>
 *
 * All the sample Java and Groovy sources to be joint-compiled must be put in <code>src/test-resources/stubgenerator</code>,
 * under a directory whose name is the name of the subclass you created, with the first letter lowercase,
 * and the suffix Test removed.
 * <br><br>
 *
 * Example: for the test <code>CircularLanguageReferenceTest</code>,
 * you should put your resources in <code>src/test-resources/stubgenerator/circularLanguageReference</code>.
 * <br><br>
 *
 * From within the <code>verifyStubs()</code> method, you can make various assertions on the stubs.
 * QDox is used for parsing the Java sources (both the generated stub Java sources, as well as the orinal Java source,
 * but not the Groovy sources).
 * The execution of the <code>verifyStubs()</code> method is done with the <code>QDoxCategory</code> applied,
 * which provides various useful shortcuts for quickly checking the structure of your stubs.
 * <br><br>
 *
 * Please have a look at the existing samples to see what kind of asserts can be done.
 *
 * @author Guillaume Laforge
 */
abstract class BaseStubTest extends GroovyTestCase {

    protected final File targetDir = createTempDirectory()
    protected final File stubDir   = createTempDirectory()

    protected JavaDocBuilder qdox = new JavaDocBuilder()


    /**
     * Prepare the target and stub directories.
     */
    protected void setUp() {
        // TODO: commented super.setUp() as it generates a stackoverflow, for some reason?!
        // super.setUp()
        println """\
            Stub generator test [${this.class.name}]
              target directory: ${targetDir.absolutePath}
                stub directory: ${stubDir.absolutePath}
        """.stripIndent()
        assert targetDir.exists()
        assert stubDir.exists()
    }

    /**
     * Delete the temporary directories.
     */
    protected void tearDown() {
        println "Deleting temporary folders"
        targetDir.deleteDir()
        stubDir.deleteDir()
        super.tearDown()
    }

    /**
     * @return the folder containing the sample Groovy and Java sources for the test
     */
    private File sourcesRelativePath() {
        def nameWithoutTest = this.class.simpleName - 'Test'
        def folder = nameWithoutTest[0].toLowerCase() + nameWithoutTest[1..-1]

        def testDirectory = new File(BaseStubTest.class.classLoader.getResource('.').toURI())
        return new File(testDirectory, "../test-resources/stubgenerator/${folder}")
    }

    /**
     * Sole JUnit test method which will delegate to the <code>verifyStubs()</code> method
     * in the subclasses of <code>BaseStubTest</code>.
     */
    void testRun() {
        // create a compiler configuration to define a place to store the stubs and compiled classes
        def config = new CompilerConfiguration()
        config.with {
            targetDirectory = targetDir
            jointCompilationOptions = [stubDir: stubDir, keepStubs: true]
        }
        def loader = new GroovyClassLoader(this.class.classLoader)

        def sources = []
        def path = sourcesRelativePath()

        if (!path && !path.exists()) {
            fail "Path doesn't exist: ${path}"
        }

        println "Sources to be compiled:"
        path.eachFileRecurse(FILES) { sourceFile ->
            if (sourceFile.name ==~ /.*(\.groovy|\.java)/) {
                // add all the source files for the compiler to compile
                sources << sourceFile
                println " -> " + sourceFile.name
            }
        }

        def cu = new JavaAwareCompilationUnit(config, loader)
        cu.addSources(sources as File[])
        try {
            cu.compile()
            println "Sources compiled successfully"
        } catch (any) {
            any.printStackTrace()
            fail "Compilation failed for stub generator test: ${path}"
        }

        // instanciates QDox for parsing the Java stubs and Java sources
        qdox.addSourceTree path
        qdox.addSourceTree stubDir

        println "Verifying the stubs"
        use (QDoxCategory) {
            verifyStubs()
        }
    }

    /**
     * All tests must implement this method, for doing
     */
    abstract void verifyStubs()

    /**
     * Method providing a useful shortcut for the subclasses, so that you can use <code>classes</code>
     * from within the <code>verifyStubs()</code> method, to access all the stubs.
     * <br><br>
     *
     * @return an array of QDox' <code>JavaClass</code>es.
     */
    protected JavaClass[] getClasses() {
        qdox.classes
    }

    /**
     * Create a temporary directory.
     *
     * @return the created temporary directory
     * @throws IOException if a temporary directory could not be created
     */
    private static File createTempDirectory() throws IOException {
        File tempDirectory = File.createTempFile("stubgentests", Long.toString(System.currentTimeMillis()))
        if (!(tempDirectory.delete())) {
            throw new IOException("Impossible to delete temporary file: ${tempDirectory.absolutePath}")
        }
        if (!(tempDirectory.mkdir())) {
            throw new IOException("Impossible to create temporary directory: ${tempDirectory.absolutePath}")
        }
        return tempDirectory
    }
}
