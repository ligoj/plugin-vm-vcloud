/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.vm.vcloud;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import jakarta.transaction.Transactional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.model.*;
import org.ligoj.app.plugin.vm.execution.Vm;
import org.ligoj.app.plugin.vm.model.VmExecution;
import org.ligoj.app.plugin.vm.model.VmOperation;
import org.ligoj.app.plugin.vm.model.VmStatus;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test class of {@link VCloudPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class VCloudPluginResourceTest extends AbstractServerTest {
	@Autowired
	private VCloudPluginResource resource;

	@Autowired
	private ParameterValueResource pvResource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	protected int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Parameter.class, Project.class, Subscription.class,
				ParameterValue.class, DelegateNode.class }, StandardCharsets.UTF_8);
		this.subscription = getSubscription("Jupiter");

		// Coverage only
		Assertions.assertEquals("service:vm:vcloud",resource.getKey());

		// Invalidate vCloud cache
		clearAllCache();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, VCloudPluginResource.KEY);
	}

	@Test
	void delete() throws Exception {
		resource.delete(subscription, false);
	}

	@Test
	void getVersion() throws Exception {
		prepareMockVersion();

		final var version = resource.getVersion(subscription);
		Assertions.assertEquals("5.5.4.2831206 Fri Jun 19 15:07:32 CEST 2015", version);
	}

	@Test
	void getLastVersion() throws IOException {
		final var lastVersion = resource.getLastVersion();
		Assertions.assertNotNull(lastVersion);
		Assertions.assertTrue(lastVersion.compareTo("2017") >= 0);
	}

	@Test
	void link() throws Exception {
		prepareMockItem();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	void getVmDetailsNotFound() {
		prepareMockHome();

		// Not find VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<a/>")));
		httpServer.start();

		final Map<String, String> parameters = new HashMap<>(
				pvResource.getNodeParameters("service:vm:vcloud:obs-fca-info"));
		parameters.put(VCloudPluginResource.PARAMETER_VM, "0");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getVmDetails(parameters)), VCloudPluginResource.PARAMETER_VM, "vcloud-vm");
	}

	@Test
	void getVmDetails() throws Exception {
		prepareMockItem();

		final Map<String, String> parameters = new HashMap<>(
				pvResource.getNodeParameters("service:vm:vcloud:obs-fca-info"));
		parameters.put(VCloudPluginResource.PARAMETER_VM, "75aa69b4-8cff-40cd-9338-000000000000");
		final var vm = resource.getVmDetails(parameters);
		checkVm(vm);
		Assertions.assertTrue(vm.isDeployed());
	}

	private void checkVm(final VCloudVm item) {
		checkItem(item);
		Assertions.assertEquals("High Performances", item.getStorageProfileName());
		Assertions.assertEquals(VmStatus.POWERED_OFF, item.getStatus());
		Assertions.assertEquals(6, item.getCpu());
		Assertions.assertFalse(item.isBusy());
		Assertions.assertEquals("vApp_BPR", item.getVApp());
		Assertions.assertEquals("48b3379b-d130-439d-9559-06bcc525c7b9", item.getVAppId());
		Assertions.assertEquals(28672, item.getRam());
	}

	@Test
	void checkSubscriptionStatus() throws Exception {
		prepareMockItem();
		final var nodeStatusWithData = resource.checkSubscriptionStatus(subscription, null,
				subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
		checkVm((VCloudVm) nodeStatusWithData.getData().get("vm"));
	}

	private void prepareMockItem() throws IOException {
		prepareMockHome();

		// Find a specific VM
		httpServer
				.stubFor(
						get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
								.withBody(IOUtils.toString(new ClassPathResource(
										"mock-server/vcloud/vcloud-query-vm-poweredoff-deployed.xml").getInputStream(),
										StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockFindAll() throws IOException {
		prepareMockHome();

		// Find a list of VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-search.xml").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	void checkStatus() throws Exception {
		prepareMockVersion();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void checkStatusAuthenticationFailed() {
		httpServer.stubFor(
				post(urlPathEqualTo("/api/sessions")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), VCloudPluginResource.PARAMETER_API, "vcloud-login");
	}

	@Test
	void checkStatusAuthenticationFailedThenSucceed() throws Exception {
		prepareMockVersion();
		httpServer.stubFor(
				post(urlPathEqualTo("/api/api/sessions")).inScenario("auth").whenScenarioStateIs(Scenario.STARTED)
						.willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)).willSetStateTo("failed"));
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions")).inScenario("auth").whenScenarioStateIs("failed")
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
		httpServer
				.stubFor(get(urlPathEqualTo("/api/admin")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/vcloud/vcloud-admin.xml").getInputStream(),
								StandardCharsets.UTF_8))));
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	void checkStatusNotAdmin() {
		prepareMockHome();
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), VCloudPluginResource.PARAMETER_API, "vcloud-admin");
	}

	@Test
	void checkStatusNotAccess() {
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription))), VCloudPluginResource.PARAMETER_API, "vcloud-admin");
	}

	private void prepareMockVersion() throws IOException {
		prepareMockHome();

		// Version from "/admin"
		httpServer
				.stubFor(get(urlPathEqualTo("/api/admin")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/vcloud/vcloud-admin.xml").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockHome() {
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
	}

	@Test
	void findAllByName() throws Exception {
		prepareMockFindAll();
		httpServer.start();

		final var projects = resource.findAllByName("service:vm:vcloud:obs-fca-info", "sc");
		Assertions.assertEquals(3, projects.size());
		checkItem(projects.get(0));
	}

	@Test
	void findAllByNameNoRight() throws Exception {
		prepareMockFindAll();
		initSpringSecurityContext("any");
		httpServer.start();
		Assertions.assertEquals(0, resource.findAllByName("service:vm:vcloud:obs-fca-info", "sc").size());
	}

	@Test
	void getConsole() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/vcloud/vcloud-console.png").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final var imageStream = resource.getConsole(subscription);
		final var outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertTrue(outputStream.toByteArray().length > 1024);
	}

	@Test
	void getConsoleNotAvailable() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();

		final var imageStream = resource.getConsole(subscription);
		final var outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertEquals(0, outputStream.toByteArray().length);
	}

	@Test
	void getConsoleError() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
		httpServer.start();

		final var imageStream = resource.getConsole(subscription);
		final var outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertEquals(0, outputStream.toByteArray().length);
	}

	@Test
	void execute() throws Exception {
		httpServer
				.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/power/action/powerOn"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		prepareMockItem();
		final var execution = newExecution(VmOperation.ON);
		resource.execute(execution);

		// New execution
		Assertions.assertEquals("sca", execution.getVm());
		Assertions.assertEquals(VmOperation.ON, execution.getOperation());
	}

	private VmExecution newExecution(final VmOperation operation) {
		final var execution = new VmExecution();
		execution.setSubscription(subscriptionRepository.findOneExpected(subscription));
		execution.setOperation(operation);
		return execution;
	}

	/**
	 * Shutdown execution requires an undeploy action.
	 */
	@Test
	void executeShutDown() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(),
						StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		httpServer.start();
		final var execution = newExecution(VmOperation.SHUTDOWN);
		resource.execute(execution);

		// New execution
		Assertions.assertEquals("sca", execution.getVm());
		Assertions.assertEquals(VmOperation.SHUTDOWN, execution.getOperation());
	}

	/**
	 * Reset a powered off VM
	 */
	@Test
	void executeReset() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredoff.xml").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer
				.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/power/action/powerOn"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		httpServer.start();
		final var execution = newExecution(VmOperation.RESET);
		resource.execute(execution);

		// New execution
		Assertions.assertEquals("sca", execution.getVm());
		Assertions.assertEquals(VmOperation.ON, execution.getOperation());
	}

	/**
	 * Power Off execution requires an undeploy action.
	 */
	@Test
	void executeOff() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(),
						StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		httpServer.start();
		final var execution = newExecution(VmOperation.OFF);
		resource.execute(execution);

		// New execution
		Assertions.assertEquals("sca", execution.getVm());
		Assertions.assertEquals(VmOperation.OFF, execution.getOperation());
	}

	/**
	 * Power Off execution requires an undeploy action.
	 */
	@Test
	void executeInvalidAction() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(),
						StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-000000000000/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_BAD_REQUEST).withBody("<Error>...</Error>")));
		httpServer.start();
		Assertions.assertEquals("vm-operation-execute", Assertions.assertThrows(BusinessException.class, () -> {
			resource.execute(newExecution(VmOperation.OFF));
		}).getMessage());
	}

	/**
	 * Shutdown execution on VM that is already powered off.
	 */
	@Test
	void executeUselessAction() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
		final var execution = newExecution(VmOperation.ON);
		resource.execute(execution);

		// No new execution
		Assertions.assertNull(execution.getOperation());
	}

	private void checkItem(final Vm item) {
		Assertions.assertEquals("75aa69b4-8cff-40cd-9338-000000000000", item.getId());
		Assertions.assertEquals("sca", item.getName());
		Assertions.assertEquals("CentOS 4/5/6/7 (64-bit)", item.getOs());
	}

}
