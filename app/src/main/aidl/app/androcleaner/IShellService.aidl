package app.androcleaner;

// Runs inside the Shizuku-started shell process.
interface IShellService {
    // Shizuku calls this transaction code to stop the service.
    void destroy() = 16777114;

    String exec(String command) = 1;
}
