package org.ovirt.vdsm.jsonrpc.client.utils;

import java.util.HashMap;
import java.util.Map;

import org.ovirt.vdsm.jsonrpc.client.JsonRpcResponse;
import org.ovirt.vdsm.jsonrpc.client.ResponseDecomposer;

public class JsonResponseUtil {
    private static final String STATUS = "status";
    private static final String DEFAULT_KEY = "info";
    private static final Map<String, Object> STATUS_DONE = new HashMap<String, Object>() {
        {
            super.put("message", "Done");
            super.put("code", 0);
        }
    };
    private static final Map<String, Object> TIMEOUT_STATUS = new HashMap<String, Object>() {
        {
            super.put("message", "Internal timeout occured");
            super.put("code", -1);
        }
    };
    private Map<String, Object> responseMap = new HashMap<>();
    private String responseKey;
    private String subtypeKey;
    private Class<?> clazz = STATUS_DONE.getClass();
    private Class<?> subTypeClazz;
    private boolean ignoreResponseKey;

    public Map<String, Object> populate(JsonRpcResponse response) {
        ResponseDecomposer decomposer = new ResponseDecomposer(response);
        if (decomposer.isError()) {
            this.responseMap = decomposer.decomposeError();
        } else if (Object[].class.equals(clazz) && this.subtypeKey != null && !this.subtypeKey.trim().isEmpty()
                && this.subTypeClazz != null) {
            Object[] array = (Object[]) decomposer.decomposeResponse(this.clazz);
            updateResponse(decomposer.decomposeTypedArray(array, this.subTypeClazz, subtypeKey));
        } else {
            updateResponse(decomposer.decomposeResponse(this.clazz));
        }
        checkAndUpdateStatus();
        return this.responseMap;
    }

    @SuppressWarnings("unchecked")
    private void updateResponse(Object object) {
        if (ignoreResponseKey) {
            this.responseMap = (Map<String, Object>) object;
        } else {
            String key = DEFAULT_KEY;
            if (this.responseKey != null && !this.responseKey.trim().isEmpty()) {
                key = responseKey;
            }
            this.responseMap.put(key, object);
        }
    }

    private void checkAndUpdateStatus() {
        if (this.responseMap.get(STATUS) == null) {
            this.responseMap.put(STATUS, STATUS_DONE);
        }
    }

    /**
     * @param responseKey
     *            - Key used to store response value in result <code>Map</code>.
     * @return this <code>JsonResponseUtil</code>.
     */
    public JsonResponseUtil withResponseKey(String responseKey) {
        this.responseKey = responseKey;
        return this;
    }

    /**
     * @param clazz- A type of response which will be use instead of default <code>Map</code>.
     * @return this <code>JsonResponseUtil</code>.
     */
    public JsonResponseUtil withResponseType(Class<?> clazz) {
        this.clazz = clazz;
        return this;
    }

    /**
     * During response decomposition we will ignore default key and use raw response structure as result
     * <code>Map</code>.
     *
     * @return this <code>JsonResponseUtil</code>.
     */
    public JsonResponseUtil withIgnoreResponseKey() {
        this.ignoreResponseKey = true;
        return this;
    }

    /**
     * @param subTypeKey - Key which is used to put subtype to result map.
     * @return this <code>JsonResponseUtil</code>.
     */
    public JsonResponseUtil withSubtypeKey(String subTypeKey) {
        this.subtypeKey = subTypeKey;
        return this;
    }

    /**
     * @param clazz - type of the subtype.
     * @return this <code>JsonResponseUtil</code>.
     */
    public JsonResponseUtil withSubTypeClazz(Class<?> clazz) {
        this.subTypeClazz = clazz;
        return this;
    }
}
