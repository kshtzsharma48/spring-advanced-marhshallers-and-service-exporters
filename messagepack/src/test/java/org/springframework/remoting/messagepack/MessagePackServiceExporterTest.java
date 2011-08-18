package org.springframework.remoting.messagepack;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.msgpack.MessagePackObject;
import org.msgpack.object.RawType;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.util.ResponseArgumentCapturingRequest;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MessagePackServiceExporterTest {


	private AnnotationConfigApplicationContext serverContext, clientContext;
	public static String HOST = "127.0.0.1";
	public static int PORT = 1995;

	public static interface ClientService extends EchoService, CatService {
		Future<MessagePackObject> hello(String name);

	}

	@Configuration
	static class MyClientConfiguration {
		@Bean
		public MessagePackRpcProxyFactoryBean<ClientService> clientService() {
			MessagePackRpcProxyFactoryBean<ClientService> svc = new MessagePackRpcProxyFactoryBean<ClientService>();
			svc.setServiceInterface(ClientService.class);
			svc.setPort(PORT);
			svc.setHost(HOST);
			return svc;
		}


	}

	@Configuration
	static class MyServerConfiguration {

		@Bean
		public DefaultEchoService service() {
			return new DefaultEchoService();
		}


		@Bean
		public EventLoopFactoryBean eventLoopFactoryBean() throws Exception {
			EventLoopFactoryBean bean = new EventLoopFactoryBean();
			return bean;
		}

		@Bean
		public MessagePackRpcServiceExporter helloService() throws Exception {
			MessagePackRpcServiceExporter exporter = new MessagePackRpcServiceExporter();
			exporter.setHost(HOST);
			exporter.setPort(PORT);
			exporter.setService(service());
			exporter.setEventLoop(eventLoopFactoryBean().getObject());
			return exporter;
		}

	}

	// local to validate results
	private DefaultEchoService defaultEchoService = new DefaultEchoService();

	private ClientService rpcClient;

	@Before
	public void begin() throws Throwable {
		serverContext = new AnnotationConfigApplicationContext(MyServerConfiguration.class);
		clientContext = new AnnotationConfigApplicationContext(MyClientConfiguration.class);
		rpcClient = clientContext.getBean(ClientService.class);
		Assert.assertNotNull("the echoService can't be null", rpcClient);

		ExecutorService ex = Executors.newCachedThreadPool();
		defaultEchoService.setExecutor(ex);

	}

	@After
	public void stop() throws Throwable {
		clientContext.stop();
		serverContext.stop();
	}

	@Test
	public void testRetrievingFlatObject() throws Throwable {

		Cat rpcCat = rpcClient.fetch();
		Cat localCat = defaultEchoService.fetch();

		Assert.assertNotNull("the cat must be non-null", rpcCat);

		Assert.assertEquals(localCat.getAge(), rpcCat.getAge());
		Assert.assertEquals(localCat.getName(), rpcCat.getName());
	}

	@Test
	public void testRpcProxyConnects() throws Throwable {
		String arg;

		arg = "You're stupid";
		Assert.assertEquals(rpcClient.echo(arg), defaultEchoService.echo(arg));

		arg = "Danger";
		Assert.assertEquals(rpcClient.alarm(arg), defaultEchoService.alarm(arg));
	}


	@Test
	public void testFutureWorks() throws Throwable {
		Future<MessagePackObject> futureResponse = rpcClient.hello("Josh");
		Assert.assertNotNull("the response can't be null", futureResponse);
		MessagePackObject messagePackObject = futureResponse.get();
		Assert.assertNotNull(messagePackObject != null);
		String helloMsg = "Josh";

		ResponseArgumentCapturingRequest request = new ResponseArgumentCapturingRequest("hello", RawType.create(helloMsg));
		defaultEchoService.hello(request, helloMsg);

		Object resultOfRpcCall = rpcClient.hello(helloMsg).get().asString();

		Assert.assertEquals("the response sent from the service is indeed the " +
				                    "same one as the one we received as client, binding to a" +
				                    " different service interface.", request.getResult(), resultOfRpcCall);
	}

	@Test
	public void testRetreivingCollections() throws Throwable {


		Cat rpcCat = rpcClient.fetch();
		Cat localCat = defaultEchoService.fetch();

		Assert.assertEquals(localCat.getFriends().size(), rpcCat.getFriends().size());
		Assert.assertEquals(localCat.getHumans().size(), rpcCat.getHumans().size());


		Set<Cat> localFriends = localCat.getFriends();
		Set<Cat> remoteFriends = rpcCat.getFriends();
		for (Cat c : remoteFriends) {
			Assert.assertTrue(localFriends.contains(c));
		}

		Set<Human> localHumans = localCat.getHumans();
		Set<Human> remoteHumans = rpcCat.getHumans();

		for (Human h : localHumans) {
			Assert.assertTrue(remoteHumans.contains(h));
		}
	}
}
