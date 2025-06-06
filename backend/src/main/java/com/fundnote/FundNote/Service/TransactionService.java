package com.fundnote.FundNote.Service;

import com.fundnote.FundNote.Entity.TransactionEntity;
import com.fundnote.FundNote.Enum.TransactionType;
import com.fundnote.FundNote.Utils.UserAuthUtil;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import com.fundnote.FundNote.Entity.Accounts;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class TransactionService {

    // Please keep the comments for learning purposes

    private static final String COLLECTION_NAME="transactions";

    public String saveTransaction(TransactionEntity transaction, HttpServletRequest request) throws ExecutionException, InterruptedException, FirebaseAuthException {

        Firestore db = FirestoreClient.getFirestore();
        String uid = (String)request.getAttribute("uid");

        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setDateCreated(new Date());
        transaction.setUserId(uid);

        // Validate transaction based on its type
        transaction.getType().validate(
                transaction.getAmount(),
                transaction.getFromAccountId(),
                transaction.getToAccountId(),
                transaction.getCategory()
        );

        // 🔼 INCOME: add amount to toAccount
        if (transaction.getType() == TransactionType.INCOME) {
            String toAccountId = transaction.getToAccountId();
            DocumentReference accountRef = db.collection("accounts").document(toAccountId);
            DocumentSnapshot accountSnapshot = accountRef.get().get();

            if (!accountSnapshot.exists()) {
                throw new IllegalArgumentException("Account not found: " + toAccountId);
            }

            Accounts account = accountSnapshot.toObject(Accounts.class);
            if (account != null && account.getUserId().equals(uid)) {
                double newAmount = account.getAmount() + transaction.getAmount();
                account.setAmount(newAmount);
                accountRef.set(account); // Save updated balance
            } else {
                throw new SecurityException("Unauthorized access to account");
            }
        }

        // EXPENSE: subtract amount to fromAccount
        if (transaction.getType() == TransactionType.EXPENSE) {
            String fromAccountId = transaction.getFromAccountId();
            DocumentReference accountRef = db.collection("accounts").document(fromAccountId);
            DocumentSnapshot accountSnapshot = accountRef.get().get();

            if (!accountSnapshot.exists()) {
                throw new IllegalArgumentException("Account not found: " + fromAccountId);
            }

            Accounts account = accountSnapshot.toObject(Accounts.class);
            if (account != null && account.getUserId().equals(uid)) {
                double newAmount = account.getAmount() - transaction.getAmount();
                account.setAmount(newAmount);
                accountRef.set(account); // Save updated balance
            } else {
                throw new SecurityException("Unauthorized access to account");
            }
        }

        DocumentReference docRef = db.collection(COLLECTION_NAME).document(transaction.getTransactionId());

        ApiFuture<WriteResult> collectionApiFuture = docRef.set(transaction);

        return "Transaction Record successfully created at: " + collectionApiFuture.get().getUpdateTime();
    }

    public TransactionEntity getTransaction (String transactionId, HttpServletRequest request) throws ExecutionException, InterruptedException {
        UserAuthUtil.verifyOwnership(COLLECTION_NAME, transactionId, request);
        Firestore db = FirestoreClient.getFirestore();

        DocumentReference docRef = db.collection(COLLECTION_NAME).document(transactionId);
        ApiFuture<DocumentSnapshot> future = docRef.get();

        DocumentSnapshot document = future.get();

        if(document.exists()) {
            return document.toObject(TransactionEntity.class);
        } else {
            throw new RuntimeException("Transaction not found with ID: " + transactionId);
        }
    }

    // Not the best practice in this case, please use other one instead. Keeping this just as a reference
    // This method is best for admins.
    public List<TransactionEntity> getTransactionsByUserId (String userId) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION_NAME).whereEqualTo("userId", userId).get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        List<TransactionEntity> transactions = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            transactions.add(document.toObject(TransactionEntity.class));
        }

        return transactions;
    }

    // Fetch currently logged-in user's transactions
    public List<TransactionEntity> getUserTransactions (HttpServletRequest request) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid"); // Fetch currently logged-in user's id

        CollectionReference collection = db.collection(COLLECTION_NAME);
        Query query = collection.whereEqualTo("userId", uid);

        ApiFuture<QuerySnapshot> future = query.get(); // Step 1: Async request
        QuerySnapshot querySnapshot = future.get(); // Step 2: Block until result arrives

        List<QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
        List<TransactionEntity> transactions = new ArrayList<>();

        for (DocumentSnapshot document : documents) {
            transactions.add(document.toObject(TransactionEntity.class));
        }

        return transactions;
    }

    public List<TransactionEntity> getUserTransactionsByMonth(int year, int month, HttpServletRequest request) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid");

        // Calculate start and end of the month
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1); // Month is 0-based in Java Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date startDate = calendar.getTime();

        calendar.add(Calendar.MONTH, 1);
        Date endDate = calendar.getTime();

        // Query
        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .whereGreaterThanOrEqualTo("dateCreated", startDate)
                .whereLessThan("dateCreated", endDate)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<TransactionEntity> transactions = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            transactions.add(document.toObject(TransactionEntity.class));
        }

        return transactions;
    }

    public List<TransactionEntity> getUserTransactionsByCategory(String category, HttpServletRequest request) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid");

        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .whereEqualTo("category", category).get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<TransactionEntity> transactions = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            transactions.add(document.toObject(TransactionEntity.class));
        }
        return transactions;
    }

//    public String updateTransaction (String transactionId,TransactionEntity updatedTransaction, HttpServletRequest request) throws ExecutionException, InterruptedException {
//
//        Firestore db = FirestoreClient.getFirestore();
//        DocumentReference docRef = db.collection(COLLECTION_NAME).document(transactionId);
//        ApiFuture<DocumentSnapshot> future = docRef.get(); // Step 1: Get the future (asynchronous result)
//        DocumentSnapshot document = future.get(); // Step 2: Block and wait for the actual data
//        // It's like saying "Give me the ApiFuture for the document snapshot" → then → "Wait for that future to complete and give me the actual document."
//
//        if(!document.exists()) {
//            throw new RuntimeException("Transaction not found.");
//        }
//
//        UserAuthUtil.verifyOwnership(COLLECTION_NAME, transactionId, request);
//
//        // Set non-editable fields
//        updatedTransaction.setTransactionId(transactionId);
//        updatedTransaction.setUserId((String) request.getAttribute("uid"));
//        updatedTransaction.setDateCreated(document.getDate("dateCreated"));
//
//        // Validate the updated transaction
//        TransactionType type = TransactionType.valueOf(updatedTransaction.getType().toString());
//        type.validate(updatedTransaction.getAmount(), updatedTransaction.getFromAccountId(), updatedTransaction.getToAccountId(), updatedTransaction.getCategory());
//
//        ApiFuture<WriteResult> writeResult = docRef.set(updatedTransaction);
//
//        return "Transaction Record successfully UPDATED at: " + writeResult.get().getUpdateTime();
//    }

    public String updateTransaction(TransactionEntity updatedTransaction, HttpServletRequest request) throws ExecutionException, InterruptedException, FirebaseAuthException {
        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid");

        // Retrieve existing transaction
        DocumentReference transactionRef = db.collection(COLLECTION_NAME).document(updatedTransaction.getTransactionId());
        DocumentSnapshot transactionSnapshot = transactionRef.get().get();

        if (!transactionSnapshot.exists()) {
            throw new IllegalArgumentException("Transaction not found: " + updatedTransaction.getTransactionId());
        }

        TransactionEntity oldTransaction = transactionSnapshot.toObject(TransactionEntity.class);
        if (oldTransaction == null || !oldTransaction.getUserId().equals(uid)) {
            throw new SecurityException("Unauthorized access to transaction");
        }

        // Reverse old transaction impact
        if (oldTransaction.getType() == TransactionType.INCOME) {
            adjustAccountBalance(db, oldTransaction.getToAccountId(), -oldTransaction.getAmount(), uid);
        } else if (oldTransaction.getType() == TransactionType.EXPENSE) {
            adjustAccountBalance(db, oldTransaction.getFromAccountId(), oldTransaction.getAmount(), uid);
        }

        // Validate updated transaction
        updatedTransaction.getType().validate(
                updatedTransaction.getAmount(),
                updatedTransaction.getFromAccountId(),
                updatedTransaction.getToAccountId(),
                updatedTransaction.getCategory()
        );

        // Apply new transaction impact
        if (updatedTransaction.getType() == TransactionType.INCOME) {
            adjustAccountBalance(db, updatedTransaction.getToAccountId(), updatedTransaction.getAmount(), uid);
        } else if (updatedTransaction.getType() == TransactionType.EXPENSE) {
            adjustAccountBalance(db, updatedTransaction.getFromAccountId(), -updatedTransaction.getAmount(), uid);
        }

        // Update metadata
        updatedTransaction.setUserId(uid);
        updatedTransaction.setDateCreated(oldTransaction.getDateCreated()); // preserve original creation date

        ApiFuture<WriteResult> future = transactionRef.set(updatedTransaction);

        return "Transaction successfully updated at: " + future.get().getUpdateTime();
    }

    private void adjustAccountBalance(Firestore db, String accountId, double amountDelta, String uid) throws ExecutionException, InterruptedException {
        if (accountId == null) return;

        DocumentReference accountRef = db.collection("accounts").document(accountId);
        DocumentSnapshot accountSnapshot = accountRef.get().get();

        if (!accountSnapshot.exists()) {
            throw new IllegalArgumentException("Account not found: " + accountId);
        }

        Accounts account = accountSnapshot.toObject(Accounts.class);
        if (account != null && account.getUserId().equals(uid)) {
            account.setAmount(account.getAmount() + amountDelta);
            accountRef.set(account);
        } else {
            throw new SecurityException("Unauthorized access to account");
        }
    }

//    public String deleteTransaction (String transactionId, HttpServletRequest request) throws ExecutionException, InterruptedException {
//        UserAuthUtil.verifyOwnership(COLLECTION_NAME, transactionId, request);
//
//        Firestore db = FirestoreClient.getFirestore();
//        DocumentReference docRef = db.collection(COLLECTION_NAME).document(transactionId);
//
//        DocumentSnapshot docSnapshot = docRef.get().get();
//        if(!docSnapshot.exists()){
//            throw new RuntimeException("Transaction not found.");
//        }
//
//        ApiFuture<WriteResult> writeResult = docRef.delete();
//        return "Transaction successfully deleted at: " + writeResult.get().getUpdateTime();
//    }

    public String deleteTransaction(String transactionId, HttpServletRequest request) throws ExecutionException, InterruptedException {
        UserAuthUtil.verifyOwnership(COLLECTION_NAME, transactionId, request);

        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid");

        DocumentReference docRef = db.collection(COLLECTION_NAME).document(transactionId);
        DocumentSnapshot docSnapshot = docRef.get().get();

        if (!docSnapshot.exists()) {
            throw new RuntimeException("Transaction not found.");
        }

        TransactionEntity transaction = docSnapshot.toObject(TransactionEntity.class);

        if (transaction == null || !transaction.getUserId().equals(uid)) {
            throw new SecurityException("Unauthorized access to transaction");
        }

        // ✅ Reverse account balance impact
        if (transaction.getType() == TransactionType.INCOME) {
            adjustAccountBalance(db, transaction.getToAccountId(), -transaction.getAmount(), uid);
        } else if (transaction.getType() == TransactionType.EXPENSE) {
            adjustAccountBalance(db, transaction.getFromAccountId(), transaction.getAmount(), uid);
        }

        // 🔻 Delete the transaction after balance is adjusted
        ApiFuture<WriteResult> writeResult = docRef.delete();

        return "Transaction successfully deleted at: " + writeResult.get().getUpdateTime();
    }


    // for development
    public String deleteAllUserTransactions(HttpServletRequest request) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        String uid = (String) request.getAttribute("uid");

        // Query all transactions where userId matches
        ApiFuture<QuerySnapshot> future = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        WriteBatch batch = db.batch();

        for (QueryDocumentSnapshot doc : documents) {
            batch.delete(doc.getReference());
        }

        if (documents.isEmpty()) {
            return "No transactions found for user.";
        }

        // Commit the batch delete
        ApiFuture<List<WriteResult>> commitFuture = batch.commit();
        commitFuture.get(); // Wait for the batch to complete

        return "Successfully deleted " + documents.size() + " transaction(s) for the user.";
    }

}
