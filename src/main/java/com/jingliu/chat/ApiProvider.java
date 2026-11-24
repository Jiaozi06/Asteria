package com.jingliu.chat;

enum ApiProvider {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "https://platform.deepseek.com/api_keys"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
    CUSTOM("自定义提供商", "", "");

    final String label;
    final String address;
    final String keyPage;

    ApiProvider(String label, String address, String keyPage) {
        this.label = label;
        this.address = address;
        this.keyPage = keyPage;
    }

    String endpoint(String input) {
        String normalized = DeepSeek.normalizeEndpoint(input);
        if (this == DEEPSEEK && (address.equals(normalized) || (address + "/v1").equals(normalized))) return "";
        return normalized;
    }

    static boolean requiresKey(String endpoint) {
        return endpoint.isEmpty() || OPENAI.address.equals(endpoint);
    }
}
