package org.springframework.remoting.thrift;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.thrift.TException;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.Server;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.obm.thrift.crm.Crm;
import org.springframework.obm.thrift.crm.Customer;
import org.springframework.stereotype.Service;
import org.springframework.util.DispatcherServletJettyConfigurationCallback;
import org.springframework.util.EndpointTestUtils;
import org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TestThriftServiceExporter {

    private Log log = LogFactory.getLog(getClass());

    private Server jettyServer;

    @Before
    public void before() throws Throwable {
        jettyServer = EndpointTestUtils.serve(new DispatcherServletJettyConfigurationCallback(ExporterConfiguration.class));
        jettyServer.start();
        if (log.isDebugEnabled()) {
            log.debug("started jetty server");
        }
    }

    @After
    public void after() throws Throwable {

        jettyServer.stop();
        if (log.isDebugEnabled()) {
            log.debug("stopped jetty server");
        }
    }

    @Test
    public void testCreateCustomerOnRpcClient() throws Throwable {

        String fn = "fn", ln = "ln", email = "email@email.com";

        ApplicationContext clientContext = new AnnotationConfigApplicationContext(ThriftProxyClientConfiguration.class);
        Crm.Iface clientCrm = clientContext.getBean(Crm.Iface.class);

        Customer customer = clientCrm.createCustomer(fn, ln, email);

        if (log.isDebugEnabled()) {
            log.debug("the response from the server is " + ToStringBuilder.reflectionToString(customer));
        }

        Assert.assertNotNull(customer);
        Assert.assertEquals(customer.getFirstName(), fn);
        Assert.assertEquals(customer.getLastName(), ln);
        Assert.assertEquals(customer.getEmail(), email);
        Assert.assertTrue(customer.getId() > 0);
    }
}

@Configuration
class ExporterConfiguration {
    @Bean
    public Crm.Iface crmServiceImpl() {
        return new CrmService();
    }

    @Bean
    public BeanNameUrlHandlerMapping beanNameUrlHandlerMapping() {
        return new BeanNameUrlHandlerMapping();
    }

    @Bean(name = "/crm")
    public ThriftServiceExporter crm() {
        ThriftServiceExporter exporter = new ThriftServiceExporter();
        exporter.setService(crmServiceImpl());
        exporter.setServiceInterface(Crm.Iface.class);
        return exporter;
    }
}

@Configuration
class ThriftProxyClientConfiguration {
    @Bean
    public ThriftProxyFactoryBean client() {
        // demonstrates how to use the protocol over HTTP
        THttpClient.Factory httpClientFactory = new THttpClient.Factory("http://localhost:8080/crm");
        TTransport tTransport = httpClientFactory.getTransport(null);

        ThriftProxyFactoryBean proxy = new ThriftProxyFactoryBean();
        proxy.setTransport(tTransport);
        proxy.setServiceInterface(Crm.class);
        return proxy;
    }
}

@Service("crm")
class CrmService implements Crm.Iface {

    private List<String> firstNames = Arrays.asList("Josh", "Oliver", "Costin", "Juergen", "Rod", "Mark", "Dave", "Arjen", "Keith", "Adam", "Mike", "Mario");
    private List<String> lastNames = Arrays.asList("Lee", "Loo", "Wi", "Li", "Humble", "Duong", "Kuo");

    private final Random lnRandom = new Random();
    private final Random fnRandom = new Random();

    private String lastName() {
        int i = lnRandom.nextInt(lastNames.size());
        return lastNames.get(i);
    }

    private String firstName() {
        int i = fnRandom.nextInt(firstNames.size());
        return firstNames.get(i);
    }

    public Customer createCustomer(String fn, String ln, String email, int id) {
        return new Customer(fn, ln, email, id);
    }

    @Override
    public Customer createCustomer(String fn, String ln, String email) throws TException {
        return createCustomer(fn, ln, email, (int) (Math.random() * 2002));
    }

    public Customer getCustomerById(int customerId) {
        String fn = firstName();
        String ln = lastName();
        return new Customer(fn, ln, fn + "@email.com", customerId);
    }
}

