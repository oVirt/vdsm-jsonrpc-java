package org.ovirt.vdsm.jsonrpc.client;

import static org.ovirt.vdsm.jsonrpc.client.utils.JsonUtils.logException;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decomposes events as <code>Map</code>.
 *
 */
public class EventDecomposer {
    private static Logger log = LoggerFactory.getLogger(ResponseDecomposer.class);
    private static ObjectMapper mapper = new ObjectMapper();

    public EventDecomposer() {
        mapper.configure(DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY, true);
    }

    /**
     * Decomposes an event to a map.
     * @param event represents event received.
     * @return Decomposed event as <code>Map</code>.
     */
    public Map<String, Object> decompose(JsonRpcEvent event) {
        try {
            return mapper.readValue(mapper.writeValueAsBytes(event.getParams()),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (IOException e) {
            logException(log, "Event decomposition failed", e);
            return null;
        }
    }
}
