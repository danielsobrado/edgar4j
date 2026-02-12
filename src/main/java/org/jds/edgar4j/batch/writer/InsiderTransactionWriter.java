package org.jds.edgar4j.batch.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.insider.InsiderTransactionService;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Batch ItemWriter for InsiderTransaction entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsiderTransactionWriter implements ItemWriter<List<InsiderTransaction>> {

    private final InsiderTransactionService insiderTransactionService;

    @Override
    public void write(List<? extends List<InsiderTransaction>> chunks) throws Exception {
        log.debug("Writing {} chunks of insider transactions", chunks.size());
        
        int totalTransactions = 0;
        
        for (List<InsiderTransaction> transactionList : chunks) {
            if (transactionList != null && !transactionList.isEmpty()) {
                try {
                    // Save all transactions in the current chunk
                    List<InsiderTransaction> savedTransactions = insiderTransactionService.saveAll(transactionList);
                    
                    totalTransactions += savedTransactions.size();
                    
                    log.debug("Successfully saved {} transactions from chunk", savedTransactions.size());
                    
                } catch (Exception e) {
                    log.error("Error saving transaction chunk: {}", e.getMessage(), e);
                    
                    // Try to save transactions individually to avoid losing all data
                    saveIndividually(transactionList);
                }
            }
        }
        
        log.info("Successfully processed {} total insider transactions", totalTransactions);
    }
    
    /**
     * Fallback method to save transactions individually when batch save fails
     */
    private void saveIndividually(List<InsiderTransaction> transactions) {
        int successCount = 0;
        int errorCount = 0;
        
        for (InsiderTransaction transaction : transactions) {
            try {
                insiderTransactionService.save(transaction);
                successCount++;
                
            } catch (Exception e) {
                errorCount++;
                log.warn("Failed to save individual transaction {}: {}", 
                        transaction.getAccessionNumber(), e.getMessage());
            }
        }
        
        log.info("Individual save completed: {} successful, {} failed", successCount, errorCount);
    }
}
