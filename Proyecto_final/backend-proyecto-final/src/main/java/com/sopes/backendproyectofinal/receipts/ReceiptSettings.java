package com.sopes.backendproyectofinal.receipts;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "receipts")
public class ReceiptSettings {
    @Min(1) @Max(1000)
    private int queueCapacity = 16;
    @Min(1) @Max(10000)
    private int maxOutstanding = 100;
    @Min(1) @Max(1000)
    private int offerTimeoutMilliseconds = 50;
    @Min(1) @Max(3600)
    private int scanSecondsPerUnit = 30;

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int value) { queueCapacity = value; }
    public int getMaxOutstanding() { return maxOutstanding; }
    public void setMaxOutstanding(int value) { maxOutstanding = value; }
    public int getOfferTimeoutMilliseconds() { return offerTimeoutMilliseconds; }
    public void setOfferTimeoutMilliseconds(int value) { offerTimeoutMilliseconds = value; }
    public int getScanSecondsPerUnit() { return scanSecondsPerUnit; }
    public void setScanSecondsPerUnit(int value) { scanSecondsPerUnit = value; }
}
