/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.clustering.cluster.singleton;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.clustering.ClusterHttpClientUtil;
import org.jboss.as.test.clustering.ViewChangeListener;
import org.jboss.as.test.clustering.ViewChangeListenerBean;
import org.jboss.as.test.clustering.ViewChangeListenerServlet;
import org.jboss.as.test.clustering.cluster.ClusterAbstractTestCase;
import org.jboss.as.test.clustering.cluster.singleton.service.MyService;
import org.jboss.as.test.clustering.cluster.singleton.service.MyServiceActivator;
import org.jboss.as.test.clustering.cluster.singleton.service.MyServiceServlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class SingletonTestCase extends ClusterAbstractTestCase {

    @Deployment(name = DEPLOYMENT_1, managed = false, testable = false)
    @TargetsContainer(CONTAINER_1)
    public static Archive<?> deployment0() {
        return createDeployment();
    }

    @Deployment(name = DEPLOYMENT_2, managed = false, testable = false)
    @TargetsContainer(CONTAINER_2)
    public static Archive<?> deployment1() {
        return createDeployment();
    }

    private static Archive<?> createDeployment() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, "singleton.war");
        war.addPackage(MyService.class.getPackage());
        war.addClasses(ViewChangeListener.class, ViewChangeListenerBean.class, ViewChangeListenerServlet.class);
        war.setManifest(new StringAsset("Manifest-Version: 1.0\nDependencies: org.jboss.msc, org.jboss.as.clustering.common, org.infinispan, org.jboss.as.clustering.singleton, org.jboss.as.server, org.jboss.marshalling, org.jgroups\n"));
        war.addAsServiceProvider(org.jboss.msc.service.ServiceActivator.class, MyServiceActivator.class);
        return war;
    }

    @Override
    protected void setUp() {
        super.setUp();
        deploy(DEPLOYMENTS);
    }

    @Test
    public void testSingletonService(
            @ArquillianResource() @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1,
            @ArquillianResource() @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2)
            throws IOException, InterruptedException, URISyntaxException {

        // Needed to be able to inject ArquillianResource
        stop(CONTAINER_2);

        DefaultHttpClient client = org.jboss.as.test.http.util.HttpClientUtils.relaxedCookieHttpClient();

        // URLs look like "http://IP:PORT/singleton/service"
        URI uri1 = MyServiceServlet.createURI(baseURL1);
        URI uri2 = MyServiceServlet.createURI(baseURL2);

        log.info("URLs are: " + uri1 + ", " + uri2);

        try {
            this.establishView(client, baseURL1, NODE_1);

            HttpResponse response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(NODE_1, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            start(CONTAINER_2);

            this.establishView(client, baseURL1, NODE_1, NODE_2);

            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            response = client.execute(new HttpGet(uri2));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            stop(CONTAINER_2);

            this.establishView(client, baseURL1, NODE_1);

            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(NODE_1, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            controller.start(CONTAINER_2);

            this.establishView(client, baseURL1, NODE_1, NODE_2);

            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            response = client.execute(new HttpGet(uri2));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            stop(CONTAINER_1);

            this.establishView(client, baseURL2, NODE_2);

            response = client.execute(new HttpGet(uri2));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(NODE_2, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            controller.start(CONTAINER_1);

            this.establishView(client, baseURL2, NODE_1, NODE_2);

            response = client.execute(new HttpGet(uri1));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }

            response = client.execute(new HttpGet(uri2));
            try {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertEquals(MyServiceActivator.PREFERRED_NODE, response.getFirstHeader("node").getValue());
            } finally {
                HttpClientUtils.closeQuietly(response);
            }
        } finally {
            HttpClientUtils.closeQuietly(client);

            deployer.undeploy(DEPLOYMENT_1);
            controller.stop(CONTAINER_1);
            deployer.undeploy(DEPLOYMENT_2);
            controller.stop(CONTAINER_2);
        }
    }

    private void establishView(HttpClient client, URL baseURL, String... members) throws URISyntaxException, IOException {
        ClusterHttpClientUtil.establishView(client, baseURL, "singleton", members);
    }
}
