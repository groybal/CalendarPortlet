/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portlet.calendar.util;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import com.liferay.portletmvc4spring.test.mock.web.portlet.MockPortletConfig;
import com.liferay.portletmvc4spring.test.mock.web.portlet.ServletWrappingPortletContext;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.AbstractContextLoader;
import org.springframework.web.context.WebApplicationContext;
import com.liferay.portletmvc4spring.DispatcherPortlet;
import com.liferay.portletmvc4spring.context.XmlPortletApplicationContext;
import org.springframework.web.servlet.ViewResolver;

/**
 * A Spring {@link ContextLoader} that establishes a mock Portlet environment and {@link
 * WebApplicationContext} so that Spring Portlet MVC stacks can be tested from within JUnit.
 */
public class MockWebApplicationContextLoader extends AbstractContextLoader {
  /** The configuration defined in the {@link MockWebApplication} annotation. */
  private MockWebApplication configuration;

  public ApplicationContext loadContext(String... locations) throws Exception {
    // Establish the portlet context and config based on the test class's MockWebApplication annotation.
    MockServletContext mockServletContext =
        new MockServletContext(configuration.webapp(), new FileSystemResourceLoader());
    final ServletWrappingPortletContext portletContext =
        new ServletWrappingPortletContext(mockServletContext);
    final MockPortletConfig portletConfig =
        new MockPortletConfig(portletContext, configuration.name());

    // Create a WebApplicationContext and initialize it with the xml and portlet configuration.
    final XmlPortletApplicationContext portletApplicationContext =
        new XmlPortletApplicationContext();
    portletContext.setAttribute(
        WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, portletApplicationContext);
    portletApplicationContext.setPortletConfig(portletConfig);
    portletApplicationContext.setConfigLocations(locations);

    // Create a DispatcherPortlet that uses the previously established WebApplicationContext.
    final DispatcherPortlet dispatcherPortlet =
        new DispatcherPortlet() {
          @Override
          protected WebApplicationContext createPortletApplicationContext(
              ApplicationContext parent) {
            return portletApplicationContext;
          }
        };

    final ViewResolver viewResolver = new MockViewResolver();

    // Add the DispatcherPortlet (and anything else you want) to the context.
    // Note: this doesn't happen until refresh is called below.
    portletApplicationContext.addBeanFactoryPostProcessor(
        new BeanFactoryPostProcessor() {
          public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            beanFactory.registerResolvableDependency(DispatcherPortlet.class, dispatcherPortlet);
            // Register any other beans here, including a ViewResolver if you are using JSPs.
            beanFactory.registerResolvableDependency(ViewResolver.class, viewResolver);
          }
        });

    // Have the context notify the portlet every time it is refreshed.
    portletApplicationContext.addApplicationListener(
        new SourceFilteringListener(
            portletApplicationContext,
            new ApplicationListener<ContextRefreshedEvent>() {
              public void onApplicationEvent(ContextRefreshedEvent event) {
                dispatcherPortlet.onApplicationEvent(event);
              }
            }));

    // Prepare the context.
    portletApplicationContext.refresh();
    portletApplicationContext.registerShutdownHook();

    // Initialize the portlet.
    dispatcherPortlet.setContextConfigLocation("");
    dispatcherPortlet.init(portletConfig);

    return portletApplicationContext;
  }

  /**
   * One of these two methods will get called before {@link #loadContext(String...)}. We just use
   * this chance to extract the configuration.
   */
  @Override
  protected String[] generateDefaultLocations(Class<?> clazz) {
    extractConfiguration(clazz);
    return super.generateDefaultLocations(clazz);
  }

  /**
   * One of these two methods will get called before {@link #loadContext(String...)}. We just use
   * this chance to extract the configuration.
   */
  @Override
  protected String[] modifyLocations(Class<?> clazz, String... locations) {
    extractConfiguration(clazz);
    return super.modifyLocations(clazz, locations);
  }

  private void extractConfiguration(Class<?> clazz) {
    configuration = AnnotationUtils.findAnnotation(clazz, MockWebApplication.class);
    if (configuration == null)
      throw new IllegalArgumentException(
          "Test class " + clazz.getName() + " must be annotated @MockWebApplication.");
  }

  @Override
  protected String getResourceSuffix() {
    return "-context.xml";
  }

  @Override
  public ApplicationContext loadContext(MergedContextConfiguration mergedConfig) throws Exception {
    return loadContext(mergedConfig.getLocations());
  }
}
