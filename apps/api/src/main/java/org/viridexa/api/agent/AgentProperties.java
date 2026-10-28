package org.viridexa.api.agent;

import java.math.BigDecimal;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "viridexa.agent")
public class AgentProperties {
    private boolean enabled;
    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "";
    private int maxModelCalls = 3, maxToolCalls = 6, maxConcurrent = 2;
    private int totalTimeoutSeconds = 30, toolTimeoutSeconds = 5, modelTimeoutSeconds = 12;
    private int userDailyRequests = 20, globalDailyRequests = 200;
    private long userDailyTokens = 30_000, globalDailyTokens = 300_000;
    private int maxOutputTokens = 1200, maxInputChars = 24_000;
    private BigDecimal inputCostPerMillion, outputCostPerMillion, userDailyCost, globalDailyCost;
    private boolean allowHttp;
    private String runtimeDirectory = ".runtime/agent";

    public void validate() {
        range(maxModelCalls, 1, 3, "max-model-calls"); range(maxToolCalls, 1, 6, "max-tool-calls");
        range(maxConcurrent, 1, 2, "max-concurrent"); range(totalTimeoutSeconds, 5, 90, "total-timeout-seconds");
        range(toolTimeoutSeconds, 1, 15, "tool-timeout-seconds"); range(modelTimeoutSeconds, 1, 30, "model-timeout-seconds");
        positive(userDailyRequests, "user-daily-requests"); positive(globalDailyRequests, "global-daily-requests");
        positive(userDailyTokens, "user-daily-tokens"); positive(globalDailyTokens, "global-daily-tokens");
        range(maxOutputTokens, 1, 1200, "max-output-tokens"); range(maxInputChars, 1, 24_000, "max-input-chars");
        if(inputCostPerMillion!=null&&inputCostPerMillion.signum()<0||outputCostPerMillion!=null&&outputCostPerMillion.signum()<0)throw new IllegalArgumentException("invalid agent token price");
        if((userDailyCost!=null||globalDailyCost!=null)&&(inputCostPerMillion==null||outputCostPerMillion==null))throw new IllegalArgumentException("Cost budgets require both provider token prices");
        if(userDailyCost!=null&&userDailyCost.signum()<=0||globalDailyCost!=null&&globalDailyCost.signum()<=0)throw new IllegalArgumentException("Cost budgets must be positive");
        if (model != null && !model.isBlank() || apiKey != null && !apiKey.isBlank()) validateEndpoint();
    }

    private void validateEndpoint() {
        try {
            URI uri = URI.create(baseUrl);
            boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || allowHttp && loopback && "http".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException("invalid agent base-url");
        } catch (RuntimeException ex) { throw new IllegalArgumentException("invalid agent base-url"); }
    }
    private static void range(int n,int min,int max,String name){if(n<min||n>max)throw new IllegalArgumentException("invalid agent "+name);}
    private static void positive(long n,String name){if(n<1)throw new IllegalArgumentException("invalid agent "+name);}

    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String v){baseUrl=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public int getMaxModelCalls(){return maxModelCalls;} public void setMaxModelCalls(int v){maxModelCalls=v;}
    public int getMaxToolCalls(){return maxToolCalls;} public void setMaxToolCalls(int v){maxToolCalls=v;}
    public int getMaxConcurrent(){return maxConcurrent;} public void setMaxConcurrent(int v){maxConcurrent=v;}
    public int getTotalTimeoutSeconds(){return totalTimeoutSeconds;} public void setTotalTimeoutSeconds(int v){totalTimeoutSeconds=v;}
    public int getToolTimeoutSeconds(){return toolTimeoutSeconds;} public void setToolTimeoutSeconds(int v){toolTimeoutSeconds=v;}
    public int getModelTimeoutSeconds(){return modelTimeoutSeconds;} public void setModelTimeoutSeconds(int v){modelTimeoutSeconds=v;}
    public int getUserDailyRequests(){return userDailyRequests;} public void setUserDailyRequests(int v){userDailyRequests=v;}
    public int getGlobalDailyRequests(){return globalDailyRequests;} public void setGlobalDailyRequests(int v){globalDailyRequests=v;}
    public long getUserDailyTokens(){return userDailyTokens;} public void setUserDailyTokens(long v){userDailyTokens=v;}
    public long getGlobalDailyTokens(){return globalDailyTokens;} public void setGlobalDailyTokens(long v){globalDailyTokens=v;}
    public int getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(int v){maxOutputTokens=v;}
    public int getMaxInputChars(){return maxInputChars;} public void setMaxInputChars(int v){maxInputChars=v;}
    public BigDecimal getInputCostPerMillion(){return inputCostPerMillion;} public void setInputCostPerMillion(BigDecimal v){inputCostPerMillion=v;}
    public BigDecimal getOutputCostPerMillion(){return outputCostPerMillion;} public void setOutputCostPerMillion(BigDecimal v){outputCostPerMillion=v;}
    public BigDecimal getUserDailyCost(){return userDailyCost;}public void setUserDailyCost(BigDecimal v){userDailyCost=v;}public BigDecimal getGlobalDailyCost(){return globalDailyCost;}public void setGlobalDailyCost(BigDecimal v){globalDailyCost=v;}
    public boolean isAllowHttp(){return allowHttp;} public void setAllowHttp(boolean v){allowHttp=v;}
    public String getRuntimeDirectory(){return runtimeDirectory;} public void setRuntimeDirectory(String v){runtimeDirectory=v;}
}
