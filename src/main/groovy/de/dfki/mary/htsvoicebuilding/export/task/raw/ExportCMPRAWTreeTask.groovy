package de.dfki.mary.htsvoicebuilding.export.task.raw

// List
import java.util.ArrayList;

// HTS Wrapper import
import de.dfki.mary.htsvoicebuilding.HTSWrapper;

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.*


/**
 *  Task which Description
 *
 */
public class ExportCMPRAWTreeTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** The label list file */
    @InputFile
    final RegularFileProperty list_file = newInputFile();

    /** The file collection of the tree files */
    @InputFiles
    final ConfigurableFileCollection input_tree_files = project.files();

    /** The input clustered mode file */
    @InputFile
    final RegularFileProperty input_model_file = newInputFile();

    /** The generated conversion script files */
    @OutputFiles
    final ConfigurableFileCollection script_files = project.files();

    /** The converted script files */
    @OutputFiles
    final ConfigurableFileCollection output_tree_files = project.files();


    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public ExportCMPRAWTreeTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generation method
     *
     */
    @TaskAction
    public void generate() {

        // Sort by filename

        def start_stream = 1;
        for (def stream: project.gradle.vb_configuration.models.cmp.streams) {

            // Get the script file
            File script_file = null;
            for (File cur_file: script_files.getFiles()) {
                if (cur_file.getName().startsWith("cv_raw_${stream.kind}.")) {
                    script_file = cur_file;
                    break;
                }
            }

            // Get the input tree file
            ArrayList<File> input_tree_file_list = new ArrayList<File>();
            for (File cur_file: input_tree_files) {
                if (cur_file.getName().startsWith("${stream.kind}_")) {
                    input_tree_file_list.add(cur_file);
                }
            }

            // Get the output tree file
            File output_tree_file = null;
            for (File cur_file: output_tree_files.getFiles()) {
                if (cur_file.getName().startsWith("${stream.kind}.")) {
                    output_tree_file = cur_file;
                    break;
                }
            }


            // Submit the execution
            workerExecutor.submit(ExportCMPRAWTreeWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(
                            list_file.getAsFile().get(),
                            start_stream,
                            input_tree_file_list,
                            input_model_file.getAsFile().get(),
                            script_file,
                            output_tree_file,
                            project.hts_wrapper,
                        );
                    }
                });

            // Next stream start
            if (stream.is_msd) {
                start_stream += stream.winfiles.size()
            } else {
                start_stream += 1
            }
        }
    }
}

/**
 *  Worker to Description
 *
 */
class ExportCMPRAWTreeWorker implements Runnable {

    /** The input tree file */
    private ArrayList<File> input_tree_files;

    /** The input model file */
    private File input_model_file;

    /** The list file */
    private File list_file;

    /** The produced script file */
    private File script_file;

    /** The converted tree file */
    private File output_tree_file;

    /** The start index of the stream */
    private int stream_id;

    /** HTS Wrapper object */
    private HTSWrapper hts_wrapper;

    /**
     *  The constructor of the worker
     *
     */
    @Inject
    public ExportCMPRAWTreeWorker(File list_file, int stream_id,
                                  ArrayList<File> input_tree_files, File input_model_file,
                                  File script_file, File output_tree_file,
                                  HTSWrapper hts_wrapper) {
        // Inputs
        this.list_file = list_file;
        this.input_tree_files = input_tree_files;
        this.input_model_file = input_model_file;

        // Outputs
        this.script_file = script_file;
        this.output_tree_file = output_tree_file;

        // Utils
        this.stream_id = stream_id;
        this.hts_wrapper = hts_wrapper;
    }


    /**
     *  Running method
     *
     */
    @Override
    public void run() {

        def tmp_dir = new File(output_tree_file.getParent().toString() + "/cmp")
        tmp_dir.mkdirs()
        def tmp_tree = new File(tmp_dir.toString(), "trees.${stream_id}")

        // Generate script file
        def script_content = "TR 2\n\n"
        for (File tree_file: input_tree_files) {
            script_content += "LT $tree_file\n"
        }
        script_content += "\n"
        script_content += "ST \"${tmp_tree.toString()}\"\n\n"
        script_file.text = script_content

        // Apply conversion
        hts_wrapper.HHEdOnMMFNoOutputArgs(script_file.toString(),
                                          list_file.toString(),
                                          input_model_file.toString(),
                                          [])

        // Move the files
        assert tmp_tree.renameTo(output_tree_file)
    }
}
