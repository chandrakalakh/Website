/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.ApplicationListener;
import org.apache.tomcat.websocket.server.WsContextListener;

/**
 * The keys and certificates used in this file are all available in svn and were
 * generated using a test CA the files for which are in the Tomcat PMC private
 * repository since not all of them are AL2 licensed.
 */
public class TestSsl extends TomcatBaseTest {

    @Test
    public void testSimpleSsl() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(new ApplicationListener(
                WsContextListener.class.getName(), false));

        TesterSupport.initSsl(tomcat);

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }

    @Test
    public void testKeyPass() throws Exception {
        TesterSupport.configureClientSsl();

        Tomcat tomcat = getTomcatInstance();

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        org.apache.catalina.Context ctxt  = tomcat.addWebapp(
                null, "/examples", appDir.getAbsolutePath());
        ctxt.addApplicationListener(new ApplicationListener(
                WsContextListener.class.getName(), false));

        TesterSupport.initSsl(tomcat, "localhost-copy1.jks", "changeit",
                "tomcatpass");

        tomcat.start();
        ByteChunk res = getUrl("https://localhost:" + getPort() +
            "/examples/servlets/servlet/HelloWorldExample");
        assertTrue(res.toString().indexOf("<h1>Hello World!</h1>") > 0);
    }


    @Test
    public void testRenegotiateWorks() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        //Assume.assumeTrue("SSL renegotiation has to be supported for this test",
        //        TesterSupport.isRenegotiationSupported(getTomcatInstance()));

        File appDir = new File(getBuildDirectory(), "webapps/examples");
        // app dir is relative to server home
        Context ctxt = tomcat.addWebapp(null, "/examples",
                appDir.getAbsolutePath());
        ctxt.addApplicationListener(new ApplicationListener(
                WsContextListener.class.getName(), false));

        TesterSupport.initSsl(tomcat);

        tomcat.start();

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, TesterSupport.getTrustManagers(), null);
        SSLSocketFactory socketFactory = sslCtx.getSocketFactory();
        SSLSocket socket = (SSLSocket) socketFactory.createSocket("localhost",
                getPort());

        OutputStream os = socket.getOutputStream();

        os.write("GET /examples/servlets/servlet/HelloWorldExample HTTP/1.1\n".getBytes());
        os.flush();

        socket.startHandshake();

        try {
            os.write("Host: localhost\n\n".getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
            fail("Re-negotiation failed");
        }

        InputStream is = socket.getInputStream();
        Reader r = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(r);
        String line = br.readLine();
        Assert.assertEquals("HTTP/1.1 200 OK", line);
        while (line != null) {
            // For debugging System.out.println(line);
            // Linux clients see a Connection Reset in some circumstances and a
            // clean close in others.
            try {
                line = br.readLine();
            } catch (IOException ioe) {
                line = null;
            }
        }
    }
}
