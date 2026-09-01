using System.Diagnostics;
using System.IO.Compression;

string? packageFile = null;
string? installDirectory = null;
string? launcher = null;
int pid = -1;

for (int i = 0; i < args.Length; i++)
{
    switch (args[i].ToLowerInvariant())
    {
        case "--pid":
            if (i + 1 < args.Length)
                int.TryParse(args[++i], out pid);
            break;

        case "--package":
            if (i + 1 < args.Length)
                packageFile = args[++i];
            break;

        case "--install":
            if (i + 1 < args.Length)
                installDirectory = args[++i];
            break;

        case "--launch":
            if (i + 1 < args.Length)
                launcher = args[++i];
            break;
    }
}

Log("============================================");
Log("PhomoriaUpdater started.");
Log($"PID={pid}");
Log($"Package={packageFile}");
Log($"InstallDirectory={installDirectory}");
Log($"Launcher={launcher}");
Log("============================================");

if (pid <= 0)
    Fail("Invalid --pid argument.", 2);

if (string.IsNullOrWhiteSpace(packageFile))
    Fail("Missing --package argument.", 2);

if (string.IsNullOrWhiteSpace(installDirectory))
    Fail("Missing --install argument.", 2);

if (string.IsNullOrWhiteSpace(launcher))
    Fail("Missing --launch argument.", 2);

packageFile = Path.GetFullPath(packageFile!);
installDirectory = Path.GetFullPath(installDirectory!);
launcher = Path.GetFullPath(launcher!);

if (!File.Exists(packageFile))
    Fail($"Update package not found: {packageFile}", 3);

if (!Directory.Exists(installDirectory))
    Fail($"Installation directory not found: {installDirectory}", 4);

if (!File.Exists(launcher))
    Fail($"Phomoria launcher not found: {launcher}", 5);

try
{
    WaitForProcess(pid);

    string tempDirectory = Path.Combine(
        Path.GetTempPath(),
        "PhomoriaUpdate-" + Guid.NewGuid().ToString("N"));

    string extractedDirectory = Path.Combine(
        tempDirectory,
        "extracted");

    string backupDirectory = Path.Combine(
        Path.GetDirectoryName(installDirectory)!,
        "Phomoria-backup-" + DateTime.Now.ToString("yyyyMMdd-HHmmss"));

    Directory.CreateDirectory(tempDirectory);

    try
    {
        Log($"Temporary directory: {tempDirectory}");
        Log("Validating update ZIP.");

        using (ZipArchive archive = ZipFile.OpenRead(packageFile))
        {
            Log($"ZIP entry count: {archive.Entries.Count}");

            bool hasApp = false;

            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                Log($"ZIP ENTRY: [{entry.FullName}] Length={entry.Length}");

                // ZIP files created by the Windows tar tool can expose
                // entry paths with backslashes instead of forward slashes.
                // Normalize both forms before checking the package layout.
                string normalizedEntryName =
                    entry.FullName
                        .Replace('\\', '/')
                        .TrimStart('/');

                if (normalizedEntryName.Equals(
                        "app/",
                        StringComparison.OrdinalIgnoreCase)
                    || normalizedEntryName.StartsWith(
                        "app/",
                        StringComparison.OrdinalIgnoreCase))
                {
                    hasApp = true;
                }
            }

            Log($"ZIP app directory detected: {hasApp}");

            if (!hasApp)
            {
                throw new Exception(
                    "Update package does not contain an app directory.");
            }
        }

        Log("ZIP validation successful.");

        Directory.CreateDirectory(extractedDirectory);

        Log("Extracting update package.");

        ZipFile.ExtractToDirectory(
            packageFile,
            extractedDirectory);

        string extractedApp = Path.Combine(
            extractedDirectory,
            "app");

        if (!Directory.Exists(extractedApp))
        {
            throw new Exception(
                "Extracted package does not contain app directory.");
        }

        Log($"Extracted app directory: {extractedApp}");

        Log($"Creating backup: {backupDirectory}");

        CopyDirectory(
            Path.Combine(installDirectory, "app"),
            Path.Combine(backupDirectory, "app"));

        Log("Backup completed.");

        string liveApp = Path.Combine(
            installDirectory,
            "app");

        string oldApp = Path.Combine(
            installDirectory,
            "app.old");

        Log("Preparing application replacement.");

        DeleteDirectoryWithRetry(oldApp);
        MoveDirectoryWithRetry(liveApp, oldApp);

        try
        {
            Log("Installing new application files.");

            CopyDirectory(extractedApp, liveApp);

            Log("New application installed.");

            DeleteDirectoryWithRetry(oldApp);

            Log("Old application removed.");
        }
        catch
        {
            Log("New application installation failed.");
            Log("Attempting rollback.");

            DeleteDirectoryWithRetry(liveApp);
            MoveDirectoryWithRetry(oldApp, liveApp);

            Log("Rollback completed.");

            throw;
        }

        Log("Cleaning temporary files.");
        DeleteDirectoryWithRetry(tempDirectory);

        Log("Starting updated Phomoria.");

        Process? newProcess = Process.Start(
            new ProcessStartInfo
            {
                FileName = launcher,
                WorkingDirectory = installDirectory,
                UseShellExecute = true
            });

        if (newProcess == null)
        {
            throw new Exception(
                "Unable to start updated Phomoria.");
        }

        Log($"Updated Phomoria started. PID={newProcess.Id}");
        Log("Update completed successfully.");

        Environment.Exit(0);
    }
    catch
    {
        try
        {
            DeleteDirectoryWithRetry(tempDirectory);
        }
        catch
        {
        }

        throw;
    }
}
catch (Exception ex)
{
    Log("============================================");
    Log("UPDATE FAILED");
    Log(ex.ToString());
    Log("============================================");

    Environment.Exit(10);
}

static void WaitForProcess(int processId)
{
    try
    {
        Process process = Process.GetProcessById(processId);

        Log($"Waiting for Phomoria PID {processId} to exit...");

        process.WaitForExit();

        Log("Phomoria process has exited.");
    }
    catch (ArgumentException)
    {
        Log("Phomoria process already exited.");
    }
}

static void CopyDirectory(string source, string destination)
{
    if (!Directory.Exists(source))
    {
        throw new DirectoryNotFoundException(
            $"Source directory not found: {source}");
    }

    Directory.CreateDirectory(destination);

    foreach (string file in Directory.GetFiles(source))
    {
        string target = Path.Combine(
            destination,
            Path.GetFileName(file));

        CopyFileWithRetry(file, target);
    }

    foreach (string directory in Directory.GetDirectories(source))
    {
        string target = Path.Combine(
            destination,
            Path.GetFileName(directory));

        CopyDirectory(directory, target);
    }
}

static void CopyFileWithRetry(
    string source,
    string destination)
{
    Exception? lastException = null;

    for (int attempt = 1; attempt <= 10; attempt++)
    {
        try
        {
            File.Copy(source, destination, true);
            return;
        }
        catch (Exception ex)
        {
            lastException = ex;

            Log(
                $"File copy retry {attempt}/10: " +
                $"{Path.GetFileName(source)}");

            Thread.Sleep(500);
        }
    }

    throw new IOException(
        $"Unable to copy file: {source}",
        lastException);
}

static void MoveDirectoryWithRetry(
    string source,
    string destination)
{
    Exception? lastException = null;

    for (int attempt = 1; attempt <= 10; attempt++)
    {
        try
        {
            Directory.Move(source, destination);
            return;
        }
        catch (Exception ex)
        {
            lastException = ex;

            Log(
                $"Directory move retry {attempt}/10: {source}");

            Thread.Sleep(500);
        }
    }

    throw new IOException(
        $"Unable to move directory: {source}",
        lastException);
}

static void DeleteDirectoryWithRetry(string directory)
{
    if (!Directory.Exists(directory))
        return;

    Exception? lastException = null;

    for (int attempt = 1; attempt <= 10; attempt++)
    {
        try
        {
            Directory.Delete(directory, true);
            return;
        }
        catch (Exception ex)
        {
            lastException = ex;

            Log(
                $"Directory delete retry {attempt}/10: {directory}");

            Thread.Sleep(500);
        }
    }

    throw new IOException(
        $"Unable to delete directory: {directory}",
        lastException);
}

static void Fail(string message, int exitCode)
{
    Log("ERROR: " + message);
    Environment.Exit(exitCode);
}

static void Log(string message)
{
    string line =
        $"[{DateTime.Now:HH:mm:ss.fff}] [UPDATER] {message}";

    Console.WriteLine(line);

    try
    {
        string logDirectory = Path.Combine(
            Environment.GetFolderPath(
                Environment.SpecialFolder.ApplicationData),
            "Phomoria");

        Directory.CreateDirectory(logDirectory);

        string logFile = Path.Combine(
            logDirectory,
            "updater.log");

        File.AppendAllText(
            logFile,
            line + Environment.NewLine);
    }
    catch
    {
    }
}
