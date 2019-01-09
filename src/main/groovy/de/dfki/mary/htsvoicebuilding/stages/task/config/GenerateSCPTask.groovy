package de.dfki.mary.htsvoicebuilding.stages.task.config

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*


/**
 *  Task to generate the SCP file listing data files
 *
 */
public class GenerateSCPTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** Directory containing the data */
    @InputDirectory
    final DirectoryProperty label_directory = newInputDirectory()

    /** Directory containing the data */
    @InputDirectory
    final DirectoryProperty data_directory = newInputDirectory()

    /** SCP file produced by the worker */
    @OutputFile
    final RegularFileProperty scp_file = newOutputFile()

    /**
     *  The constructor which defines which worker executor is going to achieve the generation job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateSCPTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {

        // Submit the execution
        workerExecutor.submit(GenerateSCPWorker.class,
                              new Action<WorkerConfiguration>() {
                @Override
                public void execute(WorkerConfiguration config) {
                    config.setIsolationMode(IsolationMode.NONE);
                    config.params(data_directory.get().getAsFileTree().getFiles(),
                                  label_directory.get().getAsFile(),
                                  scp_file.getAsFile().get());
                }
            });
    }
}


/**
 *  Worker class to generate the SCP file listing data files.
 *
 */
class GenerateSCPWorker implements Runnable {
    /** Directory containing the label */
    private File label_directory;

    /** Data files */
    private Set<File> data_files;

    /** SCP file produced by the worker */
    private File scp_file;

    /**
     *  The constructor of the worker
     *
     *  @param list_basenames_file file containing the basename list
     *  @param data_dir the directory containing the data
     *  @param scp_file the SCP file generated
     */
    @Inject
    public GenerateSCPWorker(Set<File> data_files, File label_directory, File scp_file) {
        this.data_files = data_files;
        this.label_directory = label_directory;
        this.scp_file = scp_file;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {
        def output = ""

        for (File data_file: data_files) {
            String basename = data_file.getName().take(data_file.getName().lastIndexOf('.'))
            File label_file = new File(label_directory, basename + ".lab")
            if (data_file.exists() && label_file.exists()) {
                output += "${data_file}\n"
            } else {
                println("${data_file} is ignored as it is (or the corresponding label file) not existing");
            }
        }

        scp_file.text = output
    }
}
