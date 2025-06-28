package exeptions;

/**
 *
 */
public class PrivateKeyNotFoundExeption extends RuntimeException {
    /**
     * @param message
     */
    public PrivateKeyNotFoundExeption(String message) {
        super(message);
    }
}
