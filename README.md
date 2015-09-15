# FlinkProject
Niklas Wulkow's Flink Project on Biomedical Data analysis
Analysis Pipeline; written in Scala;
Steps of workflow:
  - Read the Data
  - Do Matrix Completion
  - do SVM fitting
  - Classify files by SVM result
  - Do PageRank analysis
  - Do Cluster analysis
  - Store all results in .txt-files


The code can be found in the folder 'MyFlinkProject2'. The code I wrote can be found in the folder
'src/main/scala/de/fuberlin.de/largedataanalysis' and in 'src/main/java'.

USER ARGUMENTS:
OPTION 1:
  - Path to folder containing files from healthy people
  - Path to folder containing files from diseased people
  - Path where the Output-Folder should be created
  - Path to folder containing files for testing
  - Names of genes that are not to be considered; names seperated by commas. If all genes are to be used, type 'None'.
  - Additional Input concerning several parameters: I don't want to force the user to take an ordering of parameters into account
    but allow him to choose which he enters the following way: Write a sentence that contains a KEY WORD and a value, e.g.
    'number of SVM iterations is 100' or 'threshold = 0.8'. Sentences have to be seperated by the word 'STOP'. At the start
    and end of it there have to be quotation marks. If you leave out one parameter, a default value will be used.
    Here is how to specify every parameter:
    Parameter				Key words that must occur	Default value
    do matrix completion		completion, yes			true
    matrix completion factors		factors				10
    matrix completion iterations	completion, iterations		100
    SVM iterations			SVM, iterations			100
    SVM regularization			SVM, regularization		0.01
    SVM stepsize			SVM, stepsize			0.01
    network threshold			threshold			0.9
    maximal number of genes		maxGenes OR Genanzahl		25000
    
    An example input for the program arguments is:
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full/Healthy
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full/Diseased
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full/Testing
    hsa-mir-100, hsa-mir-1-1
    "ich will 10 factors STOP und matrix completion = yes STOP SVM iterations ist 80 STOP
    SVM regularization soll 0.015 sein STOP selected Genes = 1000 STOP threshold ist 0.8"
    
OPTION 2 differs only slightly from OPTION 1:
Instead of setting the path to the 'healthy' and the 'diseased' folder and the path where the Output-folder shall be created,
the user enters only one path to a folder that directly contains the 'healthy' and the 'diseased' folder, which must have
the names "Healthy" and "Diseased". There the Output-folder is also created.
Example:
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full
    /home/mi/nwulkow/ADL/Projekt/Data/RNA/Full/Testing
    None
    "ich will 10 factors STOP und matrix completion = yes STOP SVM iterations ist 80 STOP
    SVM regularization soll 0.015 sein STOP selected Genes = 1000 STOP threshold ist 0.8"
