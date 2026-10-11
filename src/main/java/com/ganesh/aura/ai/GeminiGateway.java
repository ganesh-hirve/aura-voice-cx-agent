package com.ganesh.aura.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public interface GeminiGateway {
    JsonNode generateContent(String systemInstruction, ArrayNode contents, ArrayNode tools);
}
