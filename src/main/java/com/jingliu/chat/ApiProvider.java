package com.jingliu.chat;

enum ApiProvider {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "https://platform.deepseek.com/api_keys", ApiProtocol.ANTHROPIC),
    OPENAI("OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/api-keys", ApiProtocol.OPENAI),
    ANTHROPIC("Anthropic", "https://api.anthropic.com", "https://platform.claude.com/settings/keys", ApiProtocol.ANTHROPIC),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com", "https://aistudio.google.com/apikey", ApiProtocol.GEMINI),
    OLLAMA("Ollama", "http://127.0.0.1:11434", "", ApiProtocol.OLLAMA),
    CUSTOM("自定义提供商", "", "", ApiProtocol.OPENAI);

    final String label;
    final String address;
    final String keyPage;
    final ApiProtocol protocol;

    ApiProvider(String label, String address, String keyPage, ApiProtocol protocol) {
        this.label = label;
        this.address = address;
        this.keyPage = keyPage;
        this.protocol = protocol;
    }

    String endpoint(String input) {
        return endpoint(input, protocol);
    }

    String endpoint(String input, ApiProtocol selected) {
        String normalized = DeepSeek.normalizeEndpoint(input);
        if (normalized == null) return null;
        String base = selected.normalizeBase(normalized);
        if (this == DEEPSEEK && (selected == ApiProtocol.ANTHROPIC || selected == ApiProtocol.OPENAI)
            && (address.equals(base) || (address + "/v1").equals(base) || (address + "/anthropic/v1").equals(base))) return "";
        if (selected == ApiProtocol.OPENAI && "https://api.openai.com".equals(base)) return OPENAI.address;
        return base;
    }

    static boolean requiresKey(String endpoint) {
        if (endpoint.isEmpty()) return true;
        try {
            String host = java.net.URI.create(endpoint).getHost();
            return "api.openai.com".equals(host) || "api.deepseek.com".equals(host)
                || "api.anthropic.com".equals(host) || "generativelanguage.googleapis.com".equals(host);
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
