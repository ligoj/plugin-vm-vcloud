package org.ligoj.app.plugin.vm.vcloud;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.vm.Vm;
import org.ligoj.app.plugin.vm.model.VmOperation;
import org.ligoj.app.plugin.vm.model.VmStatus;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import net.sf.ehcache.CacheManager;

/**
 * Test class of {@link VCloudPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class VCloudPluginResourceTest extends AbstractServerTest {
	@Autowired
	private VCloudPluginResource resource;

	@Autowired
	private ParameterValueResource pvResource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	protected int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class, DelegateNode.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();

		// Invalidate vCloud cache
		CacheManager.getInstance().getCache("curl-tokens").removeAll();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	protected int getSubscription(final String project) {
		return getSubscription(project, VCloudPluginResource.KEY);
	}

	@Test
	public void delete() throws Exception {
		resource.delete(subscription, false);
	}

	@Test
	public void getVersion() throws Exception {
		prepareMockVersion();

		final String version = resource.getVersion(subscription);
		Assertions.assertEquals("5.5.4.2831206 Fri Jun 19 15:07:32 CEST 2015", version);
	}

	@Test
	public void getLastVersion() {
		final String lastVersion = resource.getLastVersion();
		Assertions.assertNotNull(lastVersion);
		Assertions.assertTrue(lastVersion.compareTo("2017") >= 0);
	}

	@Test
	public void link() throws Exception {
		prepareMockItem();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void getVmDetailsNotFound() {
		prepareMockHome();

		// Not find VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<a/>")));
		httpServer.start();

		final Map<String, String> parameters = new HashMap<>(pvResource.getNodeParameters("service:vm:vcloud:obs-fca-info"));
		parameters.put(VCloudPluginResource.PARAMETER_VM, "0");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.getVmDetails(parameters);
		}), VCloudPluginResource.PARAMETER_VM, "vcloud-vm");
	}

	@Test
	public void getVmDetails() throws Exception {
		prepareMockItem();

		final Map<String, String> parameters = new HashMap<>(pvResource.getNodeParameters("service:vm:vcloud:obs-fca-info"));
		parameters.put(VCloudPluginResource.PARAMETER_VM, "75aa69b4-8cff-40cd-9338-9abafc7d5935");
		final VCloudVm vm = resource.getVmDetails(parameters);
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
		Assertions.assertEquals(28672, item.getRam());
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		prepareMockItem();
		final SubscriptionStatusWithData nodeStatusWithData = resource.checkSubscriptionStatus(subscription, null,
				subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
		checkVm((VCloudVm) nodeStatusWithData.getData().get("vm"));
	}

	private void prepareMockItem() throws IOException {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredoff-deployed.xml").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockFindAll() throws IOException {
		prepareMockHome();

		// Find a list of VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils
				.toString(new ClassPathResource("mock-server/vcloud/vcloud-query-search.xml").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	public void checkStatus() throws Exception {
		prepareMockVersion();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	public void checkStatusAuthenticationFailed() {
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions")).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), VCloudPluginResource.PARAMETER_API, "vcloud-login");
	}

	@Test
	public void checkStatusAuthenticationFailedThenSucceed() throws Exception {
		prepareMockVersion();
		httpServer.stubFor(post(urlPathEqualTo("/api/api/sessions")).inScenario("auth").whenScenarioStateIs(Scenario.STARTED)
				.willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)).willSetStateTo("failed"));
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions")).inScenario("auth").whenScenarioStateIs("failed")
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
		httpServer.stubFor(get(urlPathEqualTo("/api/admin")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
				IOUtils.toString(new ClassPathResource("mock-server/vcloud/vcloud-admin.xml").getInputStream(), StandardCharsets.UTF_8))));
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
		httpServer.start();
		resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
	}

	@Test
	public void checkStatusNotAdmin() {
		prepareMockHome();
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), VCloudPluginResource.PARAMETER_API, "vcloud-admin");
	}

	@Test
	public void checkStatusNotAccess() {
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription));
		}), VCloudPluginResource.PARAMETER_API, "vcloud-admin");
	}

	private void prepareMockVersion() throws IOException {
		prepareMockHome();

		// Version from "/admin"
		httpServer.stubFor(get(urlPathEqualTo("/api/admin")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(
				IOUtils.toString(new ClassPathResource("mock-server/vcloud/vcloud-admin.xml").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockHome() {
		httpServer.stubFor(post(urlPathEqualTo("/api/sessions"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withHeader("x-vcloud-authorization", "token")));
	}

	@Test
	public void findAllByName() throws Exception {
		prepareMockFindAll();
		httpServer.start();

		final List<VCloudVm> projects = resource.findAllByName("service:vm:vcloud:obs-fca-info", "sc");
		Assertions.assertEquals(3, projects.size());
		checkItem(projects.get(0));
	}

	@Test
	public void getConsole() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/vcloud/vcloud-console.png").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final StreamingOutput imageStream = resource.getConsole(subscription);
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertTrue(outputStream.toByteArray().length > 1024);
	}

	@Test
	public void getConsoleNotAvailable() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.start();

		final StreamingOutput imageStream = resource.getConsole(subscription);
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertEquals(0, outputStream.toByteArray().length);
	}

	@Test
	public void getConsoleError() throws Exception {
		prepareMockHome();
		httpServer.stubFor(get(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/screen"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
		httpServer.start();

		final StreamingOutput imageStream = resource.getConsole(subscription);
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		imageStream.write(outputStream);
		Assertions.assertEquals(0, outputStream.toByteArray().length);
	}

	@Test
	public void execute() throws Exception {
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/power/action/powerOn"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		prepareMockItem();
		resource.execute(subscription, VmOperation.ON);
	}

	/**
	 * Shutdown execution requires an undeploy action.
	 */
	@Test
	public void executeShutDown() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
				new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(), StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		httpServer.start();
		resource.execute(subscription, VmOperation.SHUTDOWN);
	}

	/**
	 * Power Off execution requires an undeploy action.
	 */
	@Test
	public void executeOff() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
				new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(), StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<Task>...</Task>")));
		httpServer.start();
		resource.execute(subscription, VmOperation.OFF);
	}

	/**
	 * Power Off execution requires an undeploy action.
	 */
	@Test
	public void executeInvalidAction() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
				new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(), StandardCharsets.UTF_8))));

		// Stub the undeploy action
		httpServer.stubFor(post(urlPathEqualTo("/api/vApp/vm-75aa69b4-8cff-40cd-9338-9abafc7d5935/action/undeploy"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_BAD_REQUEST).withBody("<Error>...</Error>")));
		httpServer.start();
		Assertions.assertEquals("vm-operation-execute", Assertions.assertThrows(BusinessException.class, () -> {
			resource.execute(subscription, VmOperation.OFF);
		}).getMessage());
	}

	/**
	 * Shutdown execution on VM that is already powered off.
	 */
	@Test
	public void executeUselessAction() throws Exception {
		prepareMockHome();

		// Find a specific VM
		httpServer.stubFor(get(urlPathEqualTo("/api/query")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
				new ClassPathResource("mock-server/vcloud/vcloud-query-vm-poweredon.xml").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		resource.execute(subscription, VmOperation.ON);
	}

	private void checkItem(final Vm item) {
		Assertions.assertEquals("75aa69b4-8cff-40cd-9338-9abafc7d5935", item.getId());
		Assertions.assertEquals("sca", item.getName());
		Assertions.assertEquals("CentOS 4/5/6/7 (64-bit)", item.getOs());
	}

}
