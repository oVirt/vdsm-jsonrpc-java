package org.ovirt.vdsm.jsonrpc.client;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class RequestBuilderTestCase {

    private final static ObjectMapper MAPPER = new ObjectMapper();
    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleRequest() throws IOException {
        // given
        String methodName = "Task.getInfo";
        String taskId = "1234";

        // when
        JsonRpcRequest request = new RequestBuilder(methodName).withParameter("taskID", taskId).build();

        // then
        assertEquals(methodName, request.getMethod());
        Map<String, Object> jsonData = MAPPER.readValue(
                MAPPER.writeValueAsBytes(request.toJson()),
                new TypeReference<HashMap<String, Object>>() {
                });
        assertEquals(methodName, jsonData.get("method"));
        Map<String, Object> params = (Map<String, Object>) jsonData.get("params");
        assertEquals(taskId, params.get("taskID"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRequestwithMap() throws IOException {
        // given
        String methodName = "VM.create";
        Map<String, Object> map = new HashMap<>();
        map.put("acpiEnable", true);
        map.put("cpuShares", "shares");
        Map<String, String> customMap = new HashMap<>();
        customMap.put("customName", "customeValue");
        map.put("custom", customMap);
        List<Map<String, String>> devices = new ArrayList<>();
        Map<String, String> device = new HashMap<>();
        device.put("domainID", "myId");
        devices.add(device);
        map.put("devices", devices);
        map.put("display", "VmDisplayType");
        map.put("kvmEnable", true);
        map.put("memSize", 1024);
        map.put("nice", 10);
        map.put("smp", 1);
        map.put("smpCoresPerSocket", 2);
        map.put("smpThreadsPerCore", 1);
        map.put("timeOffset", 5);
        map.put("transparentHugePages", false);
        map.put("vmId", UUID.randomUUID().toString());
        map.put("vmName", "MyVm");
        map.put("vmType", "kvm");

        // when
        JsonRpcRequest request = new RequestBuilder(methodName).withParameter("vmParams", map).build();

        // then
        Map<String, Object> jsonData = MAPPER.readValue(
                MAPPER.writeValueAsBytes(request.toJson()),
                new TypeReference<HashMap<String, Object>>() {
                });
        assertEquals(methodName, jsonData.get("method"));
        Map<String, Object> params = (Map<String, Object>) jsonData.get("params");
        Map<String, Object> vmParams = (Map<String, Object>) params.get("vmParams");
        assertEquals(map.get("acpiEnable"), vmParams.get("acpiEnable"));
        assertEquals(map.get("memSize"), vmParams.get("memSize"));
    }

    @Test
    public void testSimpleRequestWithOptional() throws IOException {
        // given
        String param = "1234";
        assertRequestWithOptional(param, param);
    }

    @Test
    public void testSimpleRequestWithOptionalNonString() throws IOException {
        Boolean param = Boolean.FALSE;
        assertRequestWithOptional(param, param);
    }

    @Test
    public void testSimpleRequestWithOptionalNull() throws IOException {
        Boolean param = null;
        assertRequestWithOptional(param, param);
    }

    @Test
    public void testSimpleRequestWithOptionalEmptyString() throws IOException {
        Object parameter = "";
        assertRequestWithOptional(parameter, null);
    }

    @Test
    public void testSimpleRequestWithOptionalPrimitiveBoolean() throws IOException {
        boolean parameter = true;
        assertRequestWithOptional(parameter, parameter);
    }

    @Test
    public void testSimpleRequestWithOptionalPrimitiveInteger() throws IOException {
        int parameter = 444;
        assertRequestWithOptional(parameter, parameter);
    }

    @SuppressWarnings("unchecked")
    private void assertRequestWithOptional(Object parameter, Object expected) throws IOException {
        // when
        JsonRpcRequest request = new RequestBuilder("Task.getInfo")
                .withOptionalParameter("taskID", parameter)
                .build();

        // then
        Map<String, Object> jsonData = MAPPER.readValue(
                MAPPER.writeValueAsBytes(request.toJson()),
                new TypeReference<HashMap<String, Object>>() {
                });
        Map<String, Object> params = (Map<String, Object>) jsonData.get("params");
        assertEquals(expected, params.get("taskID"));
    }
}
