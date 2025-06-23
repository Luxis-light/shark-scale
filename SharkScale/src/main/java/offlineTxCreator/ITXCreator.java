package offlineTxCreator;

/**
 *
 */
public interface ITXCreator {

	/**
	 * @param signedTransaction
	 * @return
	 */
	boolean createTransaction(SignedTransaction signedTransaction);
	
}
