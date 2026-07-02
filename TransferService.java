package com.example.bankservice.service;

import com.example.bankservice.entity.Accounts;
import com.example.bankservice.entity.Transfers;
import com.example.bankservice.model.Account;
import com.example.bankservice.model.Statistic;
import com.example.bankservice.model.TransactionStatus;
import com.example.bankservice.model.TransferRequest;
import com.example.bankservice.model.TransferResponse;
import com.example.bankservice.repository.AccountRepository;
import com.example.bankservice.repository.TransfersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransfersRepository transfersRepository;
    private final TransactionPublisher publisher;

    public TransferService(AccountRepository accountRepository, TransfersRepository transfersRepository,
                          TransactionPublisher publisher) {
        this.accountRepository = accountRepository;
        this.transfersRepository = transfersRepository;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        List<Accounts> entities = accountRepository.findByAccountStatusActive();
        return entities.stream()
                .map(a -> new Account(
                        String.valueOf(a.getAccountId()),
                        a.getAccountNumber(),
                        a.getCustomer().getCustomerNumber(),
                        a.getAccountType(),
                        a.getAccountStatus(),
                        a.getBalance()))
                .collect(Collectors.toList());
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        Optional<Accounts> maybeFrom = accountRepository.findByAccountNumber(request.fromAccountId());
        Optional<Accounts> maybeTo = accountRepository.findByAccountNumber(request.toAccountId());

        if (maybeFrom.isEmpty() || maybeTo.isEmpty()) {
            return new TransferResponse(null, TransactionStatus.FAILED);
        }

        Accounts from = maybeFrom.get();
        Accounts to = maybeTo.get();

        if (request.amount().compareTo(from.getBalance()) > 0) {
            return new TransferResponse(null, TransactionStatus.FAILED);
        }

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));

        accountRepository.save(from);
        accountRepository.save(to);

        String debitTxnId = "D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String creditTxnId = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String transferId = "TF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Transfers transferEntity = new Transfers(transferId, debitTxnId, creditTxnId);
        transfersRepository.save(transferEntity);

        // Publish statistics for COMPLETED transaction (two legs: TRANSFER_OUT and TRANSFER_IN)
        publisher.publish("TRANSFER_OUT", new Statistic("TRANSFER_OUT", request.amount()));
        publisher.publish("TRANSFER_IN", new Statistic("TRANSFER_IN", request.amount()));

        return new TransferResponse(transferId, TransactionStatus.COMPLETE);
    }

}