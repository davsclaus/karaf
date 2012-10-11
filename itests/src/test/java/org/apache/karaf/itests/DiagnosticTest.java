/*
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
 */
package org.apache.karaf.itests;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.ExamReactorStrategy;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.spi.reactors.EagerSingleStagedReactorFactory;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import static org.junit.Assert.assertTrue;

@RunWith(JUnit4TestRunner.class)
@ExamReactorStrategy(EagerSingleStagedReactorFactory.class)
public class DiagnosticTest extends KarafTestSupport {

    @Test
    public void dumpCreateCommand() throws Exception {
        String dumpCreateOutput = executeCommand("dev:dump-create");
        System.out.println(dumpCreateOutput);
        assertTrue(dumpCreateOutput.contains("Diagnostic dump created."));
    }

    @Test
    public void createDumpViaMBean() throws Exception {
        JMXConnector connector = null;
        try {
            connector = this.getJMXConnector();
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            ObjectName name = new ObjectName("org.apache.karaf:type=diagnostic,name=root");
            connection.invoke(name, "createDump", new Object[]{ "itest" }, new String[]{ "java.lang.String" });
        } finally {
            if (connector != null)
                connector.close();
        }
    }

}
