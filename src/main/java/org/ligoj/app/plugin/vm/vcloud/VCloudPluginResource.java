package org.ligoj.app.plugin.vm.vcloud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.plugin.vm.VmResource;
import org.ligoj.app.plugin.vm.dao.VmScheduleRepository;
import org.ligoj.app.plugin.vm.execution.Vm;
import org.ligoj.app.plugin.vm.execution.VmExecutionServicePlugin;
import org.ligoj.app.plugin.vm.model.VmExecution;
import org.ligoj.app.plugin.vm.model.VmOperation;
import org.ligoj.app.plugin.vm.model.VmStatus;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.XmlUtils;
import org.ligoj.bootstrap.core.curl.CurlCacheToken;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import lombok.extern.slf4j.Slf4j;

/**
 * vCloud VM resource.
 */
@Path(VCloudPluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class VCloudPluginResource extends AbstractToolPluginResource
		implements VmExecutionServicePlugin, InitializingBean {

	/**
	 * Plug-in key.
	 */
	public static final String URL = VmResource.SERVICE_URL + "/vcloud";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * vCloud API base URL. Not the portal URL.
	 *
	 * @see "https://api.sample.com/api"
	 */
	public static final String PARAMETER_API = KEY + ":api";

	/**
	 * Optional public portal (vCloud director) URL
	 *
	 * @see "https://sample.com/portal"
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * vCloud user name.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * vCloud password able to perform VM operations.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * vCloud organization.
	 */
	public static final String PARAMETER_ORGANIZATION = KEY + ":organization";

	/**
	 * The managed VM identifier.
	 */
	public static final String PARAMETER_VM = KEY + ":id";

	private static final Map<VmOperation, String> OPERATION_TO_VCLOUD = new EnumMap<>(VmOperation.class);

	/**
	 * Mapping table giving the operation to execute depending on the requested operation and the status of the VM.
	 * <TABLE summary="Mapping Table">
	 * <THEAD>
	 * <TR>
	 * <TH>status</TH>
	 * <TH>operation demandée</TH>
	 * <TH>operation exécutée</TH>
	 * </TR>
	 * </THEAD> <TBODY>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>shutdown</TD>
	 * <TD>-</TD>
	 * </TR>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>power off</TD>
	 * <TD>-</TD>
	 * </TR>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>resume</TD>
	 * <TD>resume</TD>
	 * </TR>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>suspend</TD>
	 * <TD>-</TD>
	 * </TR>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>reset</TD>
	 * <TD>resume</TD>
	 * </TR>
	 * <TR>
	 * <TD>off</TD>
	 * <TD>restart</TD>
	 * <TD>resume</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>shutdown</TD>
	 * <TD>shutdown</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>power off</TD>
	 * <TD>power off</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>resume</TD>
	 * <TD>-</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>suspend</TD>
	 * <TD>suspend</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>reset</TD>
	 * <TD>reset</TD>
	 * </TR>
	 * <TR>
	 * <TD>on</TD>
	 * <TD>restart</TD>
	 * <TD>restart</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>shutdown</TD>
	 * <TD>power off</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>power off</TD>
	 * <TD>power off</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>resume</TD>
	 * <TD>resume</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>suspend</TD>
	 * <TD>-</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>reset</TD>
	 * <TD>reset</TD>
	 * </TR>
	 * <TR>
	 * <TD>suspended</TD>
	 * <TD>restart</TD>
	 * <TD>reset</TD>
	 * </TR>
	 * </TBODY>
	 * </TABLE>
	 */
	private static final Map<VmStatus, Map<VmOperation, VmOperation>> FAILSAFE_OPERATIONS = new EnumMap<>(
			VmStatus.class);

	@Autowired
	private CurlCacheToken curlCacheToken;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	private VmScheduleRepository vmScheduleRepository;

	@Value("${saas.service-vm-vcloud-auth-retries:2}")
	private int retries;

	@Value("${saas.service-vm-vcloud-auth-timeout:5000}")
	private int timeout;

	@Autowired
	protected XmlUtils xml;

	/**
	 * Cache the API token.
	 *
	 * @param url            The remote URL. Trailing <code>/</code> is removed.
	 * @param authentication Credential.
	 * @param processor      The CURL processor with pre-authenticated token.
	 * @return The remote resource content. My be <code>null</code> with error.
	 */
	private String authenticate(final String url, final String authentication, final VCloudCurlProcessor processor) {
		return curlCacheToken.getTokenCache(VCloudPluginResource.class, url + "##" + authentication, k -> {

			// Authentication request
			final var request = new CurlRequest(HttpMethod.POST, url, null, VCloudCurlProcessor.LOGIN_CALLBACK,
					"Authorization:Basic " + authentication);

			// Execute with a timeout
			request.setTimeout(timeout);
			processor.process(Collections.singletonList(request));
			return processor.token;
		}, retries, () -> new ValidationJsonException(PARAMETER_API, "vcloud-login"));
	}

	/**
	 * Prepare an authenticated connection to vCloud. The given processor would be updated with the security token.
	 */
	private void authenticate(final Map<String, String> parameters, final VCloudCurlProcessor processor) {
		final var user = parameters.get(PARAMETER_USER);
		final var password = StringUtils.trimToEmpty(parameters.get(PARAMETER_PASSWORD));
		final var organization = StringUtils.trimToEmpty(parameters.get(PARAMETER_ORGANIZATION));
		final var url = StringUtils.appendIfMissing(parameters.get(PARAMETER_API), "/") + "sessions";

		// Encode the authentication 'user@organization:password'
		final var authentication = Base64
				.encodeBase64String((user + "@" + organization + ":" + password).getBytes(StandardCharsets.UTF_8));

		// Authentication request using cache
		processor.setToken(authenticate(url, authentication, processor));
	}

	@Override
	public VCloudVm getVmDetails(final Map<String, String> parameters)
			throws SAXException, IOException, ParserConfigurationException {

		final var id = parameters.get(PARAMETER_VM);
		// Get the VM if exists
		final var vms = toVms(getVCloudResource(parameters,
				"/query?type=vm&format=idrecords&filter=id==urn:vcloud:vm:" + id + "&pageSize=1"));

		// Check the VM has been found
		if (vms.isEmpty()) {
			// Invalid id
			throw new ValidationJsonException(PARAMETER_VM, "vcloud-vm", id);
		}
		return vms.get(0);
	}

	@Override
	public void link(final int subscription) throws Exception {
		// Validate the virtual machine name
		getVmDetails(subscriptionResource.getParameters(subscription));
	}

	/**
	 * Find the virtual machines matching to the given criteria. Look into virtual machine name only.
	 *
	 * @param node     the node to be tested with given parameters.
	 * @param criteria the search criteria. Case is insensitive.
	 * @return virtual machines.
	 * @throws IOException                  When VM definition XML cannot be retrieved.
	 * @throws SAXException                 When the XML validation failed.
	 * @throws ParserConfigurationException When the XML parsing failed.
	 */
	@GET
	@Path("{node:service:.+}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<VCloudVm> findAllByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria)
			throws IOException, SAXException, ParserConfigurationException {
		// Check the node exists
		if (nodeRepository.findOneVisible(node, securityHelper.getLogin()) == null) {
			return Collections.emptyList();
		}

		// Get the VMs and parse them
		return toVms(
				getVCloudResource(pvResource.getNodeParameters(node), "/query?type=vm&format=idrecords&filter=name==*"
						+ criteria + "*&sortAsc=name&fields=name,guestOs&pageSize=10"));
	}

	/**
	 * Return a snapshot of the console.
	 *
	 * @param subscription the valid screenshot of the console.
	 * @return the valid screenshot of the console.
	 */
	@GET
	@Path("{subscription:\\d+}/console.png")
	@Produces("image/png")
	public StreamingOutput getConsole(@PathParam("subscription") final int subscription) {
		final var parameters = subscriptionResource.getParameters(subscription);
		final var processor = new VCloudCurlProcessor();
		authenticate(parameters, processor);

		// Get the screen thumbnail
		return output -> {
			final var url = StringUtils.appendIfMissing(parameters.get(PARAMETER_API), "/") + "vApp/vm-"
					+ parameters.get(PARAMETER_VM) + "/screen";
			final var curlRequest = new CurlRequest("GET", url, null, (request, response) -> {
				if (response.getStatusLine().getStatusCode() == HttpServletResponse.SC_OK) {
					// Copy the stream
					IOUtils.copy(response.getEntity().getContent(), output);
					output.flush();
				}
				return false;
			});
			processor.process(curlRequest);
		};
	}

	/**
	 * Build a described {@link Vm} bean from a XML VMRecord entry.
	 */
	private VCloudVm toVm(final Element record) {
		final var result = new VCloudVm();
		result.setId(StringUtils.removeStart(record.getAttribute("id"), "urn:vcloud:vm:"));
		result.setName(record.getAttribute("name"));
		result.setOs(record.getAttribute("guestOs"));

		// Optional attributes
		result.setStorageProfileName(record.getAttribute("storageProfileName"));
		result.setStatus(EnumUtils.getEnum(VmStatus.class, record.getAttribute("status")));
		result.setCpu(NumberUtils.toInt(StringUtils.trimToNull(record.getAttribute("numberOfCpus"))));
		result.setBusy(Boolean.parseBoolean(
				ObjectUtils.defaultIfNull(StringUtils.trimToNull(record.getAttribute("isBusy")), "false")));
		result.setVApp(StringUtils.trimToNull(record.getAttribute("containerName")));
		result.setVAppId(
				StringUtils.trimToNull(StringUtils.removeStart(record.getAttribute("container"), "urn:vcloud:vapp:")));
		result.setRam(NumberUtils.toInt(StringUtils.trimToNull(record.getAttribute("memoryMB"))));
		result.setDeployed(Boolean.parseBoolean(
				ObjectUtils.defaultIfNull(StringUtils.trimToNull(record.getAttribute("isDeployed")), "false")));
		return result;
	}

	/**
	 * Build described beans from a XML result.
	 */
	private List<VCloudVm> toVms(final String vmAsXml) throws SAXException, IOException, ParserConfigurationException {
		final var tags = xml.getTags(vmAsXml, "VMRecord");
		return IntStream.range(0, tags.getLength()).mapToObj(tags::item).map(n -> (Element) n).map(this::toVm).toList();
	}

	/**
	 * Return a vCloud's resource after an authentication. Return <code>null</code> when the resource is not found.
	 * Authentication will be done to get the data.
	 *
	 * @param parameters The subscription parameters.
	 * @param resource   The remote vCloud resource.
	 * @return The remote resource content. My be <code>null</code> with error.
	 */
	private String getVCloudResource(final Map<String, String> parameters, final String resource) {
		return authenticateAndExecute(parameters, HttpMethod.GET, resource);
	}

	/**
	 * Return a vCloud's resource after an authentication. Return <code>null</code> when the resource is not found.
	 * Authentication is started from there.
	 *
	 * @param parameters The subscription parameters.
	 * @param method     The HTTP method.
	 * @param resource   The remote resource.
	 * @return The remote resource content. My be <code>null</code> with error.
	 */
	private String authenticateAndExecute(final Map<String, String> parameters, final String method,
			final String resource) {
		final var processor = new VCloudCurlProcessor();
		authenticate(parameters, processor);
		return execute(processor, method, parameters.get(PARAMETER_API), resource);
	}

	/**
	 * Return/execute a vCloud resource. Return <code>null</code> when the resource is not found. Authentication should
	 * be proceeded before for authenticated query.
	 *
	 * @param processor The CURL processor with pre-authenticated token.
	 * @param method    HTTP method.
	 * @param url       The remote URL. Trailing <code>/</code> is removed.
	 * @param resource  The remote resource.
	 * @return The remote resource content. My be <code>null</code> with error.
	 */
	private String execute(final CurlProcessor processor, final String method, final String url,
			final String resource) {
		// Get the resource using the preempted authentication
		final var request = new CurlRequest(method,
				StringUtils.appendIfMissing(url, "/") + StringUtils.removeStart(resource, "/"), null);
		request.setSaveResponse(true);

		// Execute the requests
		processor.process(request);
		processor.close();
		return request.getResponse();
	}

	@Override
	public String getKey() {
		return VCloudPluginResource.KEY;
	}

	/**
	 * Check the server is available with administration right.
	 *
	 * @param parameters Subscription parameters.
	 * @throws Exception when version cannot be retrieved.
	 */
	private void validateAdminAccess(final Map<String, String> parameters) throws Exception {
		if (getVersion(parameters) == null) {
			throw new ValidationJsonException(PARAMETER_API, "vcloud-admin");
		}
	}

	@Override
	public String getVersion(final Map<String, String> parameters) throws Exception {
		return StringUtils.trimToNull(
				xml.getTags(ObjectUtils.defaultIfNull(getVCloudResource(parameters, "/admin"), "<a><Description/></a>"),
						"Description").item(0).getTextContent());
	}

	@Override
	public String getLastVersion() {
		// Get the download JSON from the default repository
		try (var curl = new CurlProcessor()) {
			final var portletVersions = curl.get(
					"https://my.vmware.com/web/vmware/downloads?p_p_id=ProductIndexPortlet_WAR_itdownloadsportlet&p_p_lifecycle=2&p_p_resource_id=allProducts");

			// Extract the version from the raw String, because of the non stable
			// content format, but the links
			// Search for : "target":
			// "./info/slug/datacenter_cloud_infrastructure/vmware_vcloud_suite/6_0"
			final var linkIndex = Math
					.min(ObjectUtils.defaultIfNull(portletVersions, "").indexOf("vmware_vcloud_suite/")
							+ "vmware_vcloud_suite/".length(), portletVersions.length());
			return portletVersions.substring(linkIndex,
					Math.min(Math.max(portletVersions.indexOf('#', linkIndex), linkIndex),
							Math.max(portletVersions.indexOf('\"', linkIndex), linkIndex)));
		}
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) throws Exception {
		// Status is UP <=> Administration access is UP (if defined)
		validateAdminAccess(parameters);
		return true;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final int subscription, final String node,
			final Map<String, String> parameters) throws Exception { // NOSONAR
		final var status = new SubscriptionStatusWithData();
		status.put("vm", getVmDetails(parameters));
		status.put("schedules", vmScheduleRepository.countBySubscription(subscription));
		return status;
	}

	@Override
	public void execute(final VmExecution execution) throws Exception {
		final int subscription = execution.getSubscription().getId();
		final var operation = execution.getOperation();
		final var parameters = subscriptionResource.getParametersNoCheck(subscription);
		final var vmUrl = "/vApp/vm-" + parameters.get(PARAMETER_VM);

		// First get VM state
		final var vm = getVmDetails(parameters);
		final var status = vm.getStatus();

		// Share the VM name and current status to the execution
		execution.setVm(vm.getName());
		execution.setPreviousState(status);

		// Get the right operation depending on the current state
		final var operationF = failSafeOperation(status, operation);
		execution.setOperation(operationF);
		if (operationF == null) {
			// Final operation is considered as useless
			log.info("Requested operation {} is marked as useless considering the status {} of vm {}", operation,
					status, parameters.get(PARAMETER_VM));
			return;
		}

		final var action = MapUtils.getObject(OPERATION_TO_VCLOUD, operationF,
				operationF.name().toLowerCase(Locale.ENGLISH));

		// Check if undeployment is requested to shutdown completely the VM
		if (operationF == VmOperation.SHUTDOWN || operationF == VmOperation.OFF) {
			// The requested operation needs the VM to be undeployed
			final var content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><UndeployVAppParams xmlns=\"http://www.vmware.com/vcloud/v1.5\"><UndeployPowerAction>"
					+ action + "</UndeployPowerAction></UndeployVAppParams>";
			final var url = StringUtils.removeEnd(parameters.get(PARAMETER_API), "/");
			final var request = new CurlRequest(HttpMethod.POST, url + vmUrl + "/action/undeploy", content,
					"Content-Type:application/vnd.vmware.vcloud.undeployVAppParams+xml");
			request.setSaveResponse(true);

			// Use the preempted authentication
			final var processor = new VCloudCurlProcessor();
			authenticate(parameters, processor);

			// Execute the request
			processor.process(request);
			processor.close();

			checkSchedulerResponse(request.getResponse());
		} else {
			// Operation does not require to undeploy the VM
			checkSchedulerResponse(
					authenticateAndExecute(parameters, HttpMethod.POST, vmUrl + "/power/action/" + action));
		}
	}

	/**
	 * Check the response is valid. For now, the response must not be <code>null</code>.
	 */
	private void checkSchedulerResponse(final String response) {
		if (response == null) {
			// The result is not correct
			throw new BusinessException("vm-operation-execute");
		}
	}

	/**
	 * Decide the best operation suiting to the required operation and depending on the current status of the virtual
	 * machine.
	 *
	 * @param status    The current status of the VM.
	 * @param operation The requested operation.
	 * @return The failsafe operation suiting to the current status of the VM. Return <code>null</code> when the
	 *         computed operation is irreleavant.
	 */
	private VmOperation failSafeOperation(final VmStatus status, final VmOperation operation) {
		if (FAILSAFE_OPERATIONS.get(status).containsKey(operation)) {
			// Mapped operation
			return FAILSAFE_OPERATIONS.get(status).get(operation);
		}

		// Ignored operation
		return null;
	}

	/**
	 * Register a mapping Status+operation to operation.
	 *
	 * @param status            The current status.
	 * @param operation         The requested operation
	 * @param operationFailSafe The computed operation.
	 */
	private void registerOperation(final VmStatus status, final VmOperation operation,
			final VmOperation operationFailSafe) {
		FAILSAFE_OPERATIONS.computeIfAbsent(status, s -> new EnumMap<>(VmOperation.class));
		FAILSAFE_OPERATIONS.get(status).put(operation, operationFailSafe);
	}

	@Override
	public void afterPropertiesSet() {
		OPERATION_TO_VCLOUD.put(VmOperation.OFF, "powerOff");
		OPERATION_TO_VCLOUD.put(VmOperation.ON, "powerOn");

		// Powered off status
		registerOperation(VmStatus.POWERED_OFF, VmOperation.ON, VmOperation.ON);
		registerOperation(VmStatus.POWERED_OFF, VmOperation.RESET, VmOperation.ON);
		registerOperation(VmStatus.POWERED_OFF, VmOperation.REBOOT, VmOperation.ON);

		// Powered on status
		registerOperation(VmStatus.POWERED_ON, VmOperation.SHUTDOWN, VmOperation.SHUTDOWN);
		registerOperation(VmStatus.POWERED_ON, VmOperation.OFF, VmOperation.OFF);
		registerOperation(VmStatus.POWERED_ON, VmOperation.SUSPEND, VmOperation.SUSPEND);
		registerOperation(VmStatus.POWERED_ON, VmOperation.RESET, VmOperation.RESET);
		registerOperation(VmStatus.POWERED_ON, VmOperation.REBOOT, VmOperation.REBOOT);

		// Suspended status
		registerOperation(VmStatus.SUSPENDED, VmOperation.SHUTDOWN, VmOperation.OFF);
		registerOperation(VmStatus.SUSPENDED, VmOperation.OFF, VmOperation.OFF);
		registerOperation(VmStatus.SUSPENDED, VmOperation.ON, VmOperation.ON);
		registerOperation(VmStatus.SUSPENDED, VmOperation.RESET, VmOperation.RESET);
		registerOperation(VmStatus.SUSPENDED, VmOperation.REBOOT, VmOperation.RESET);
	}
}
