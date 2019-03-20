#!/usr/bin/perl
use lib "utils";
use warnings;
use strict;

################################################################################
#
# File: HERest.pl
# A perl script for parellel processing of the command 'HERest' from HTK
#
# It seems that this parellel implelmenation of the 'HERest' does NOT produce
#   the same result. According to the HTK book, it seems that we need to put
#   equal number of data files into each of the processor which is not always
#   possible. So it's still experimental.
#
# Usage:
#	HERest.pl [options] hmmList dataFiles...
#
# For details about the usage, check it by typing "HERest" without any options.
#
# This perl script is designed to run transparently, e.g., you can run this
#   script as if you run 'HERest'.
#
# This script submits parellel jobs through the SGE (Sun Grid Engine) using
#   an SGE command 'qsub' and checks the job progress using 'qstat'
#
# It returns when all the parellel jobs are finished
#
# Written by Bowon Lee, 02/22/2006
#
# Department of the Electrical and Computer Engineering
# University of Illinois at Urbana-Champaign
#
# Adapted for HTS by Sébastien Le Maguer based on indications from Junichi Yamagishi, 03/07/2009
#
################################################################################

# Specify the command to be executed
my $COMMAND = "HSMMAlign";
my $nbproc  = shift(@ARGV);

# Check my user ID
my $USERID = readpipe("whoami");

# Check for the input script file following the option '-S'
# and output HMM model file following the option '-M'
my @ARGIN = @ARGV;
my ( $NSCP, $NMMF );
foreach my $n ( 0 .. $#ARGIN ) {
    $NSCP = $n + 1 if ( $ARGIN[$n] eq "-S" );
}
my $scpi = "$ARGIN[$NSCP]";

# Open the input script and compute the script size for each processor
open( SCP, "$scpi" ) || die "Cannot open $scpi: $!";
my $NLINES = 0;
foreach (<SCP>) {
    $NLINES += 1;
}
my $SCPSIZE = int( $NLINES / $nbproc );
close(SCP);

# Create a list of divided data set
my @scpn = ();
foreach my $n ( 1 .. $nbproc ) {
    $scpn[ $n - 1 ] = "$scpi";
    $scpn[ $n - 1 ] =~ s/(.*)(\..*)/$1\_$n$2/g;
}

# Divide the data set and write them into each script file
my $n      = 0;
my $nlines = 0;
open( SCP, "$scpi" );
foreach my $line (<SCP>) {
    if ( ( $nlines == $SCPSIZE * $n ) && ( $n != $nbproc ) ) {
        close(SCPPL);
        open( SCPPL, ">$scpn[$n]" );
        $n = $n + 1;
    }
    print SCPPL "$line";
    $nlines += 1;
}
close(SCPPL);
close(SCP);

# Create command for each processor
my @commands = ();
foreach my $n ( 1 .. $nbproc ) {
    $commands[ $n - 1 ] = "$COMMAND";
    foreach my $narg ( 0 .. $#ARGIN - 1 ) {
        unless ( $narg == $NSCP ) {
            if ( $ARGIN[$narg] =~ m/\*/ ) {
                $commands[ $n - 1 ] = "$commands[$n-1] '$ARGIN[$narg]'";
            }
            else {
                $commands[ $n - 1 ] = "$commands[$n-1] $ARGIN[$narg]";
            }
        }
        $commands[ $n - 1 ] = "$commands[$n-1] $scpn[$n-1]"
          if ( $narg == $NSCP );
    }
    $commands[ $n - 1 ] = "$commands[$n-1] $ARGIN[$#ARGIN]";
}
open( SCP, "$scpi" ) || die "Cannot open $scpi: $!";

# Write script for each processor and submit the job
my @com = ();
foreach my $n ( 0 .. $nbproc - 1 ) {
    my $scps = "$n.sh";
    open( SGESCP, ">$scps" ) || die "Cannot open $scps: $!";
    print SGESCP '#!/bin/bash';
    print SGESCP "\n";
    print SGESCP '#$ -S /bin/bash';
    print SGESCP "\n";
    print SGESCP '#$ -cwd';
    print SGESCP "\n";
    print SGESCP "\n";
    print SGESCP "$commands[$n]\n";

    push( @com, $scps );
}

my @pid = ();
my $p;
foreach $n ( 0 .. $nbproc - 1 ) {
    $p = fork();
    if ( $p != 0 ) {
        push( @pid, $p );
    }
    else {
        system("bash $com[$n]");
        exit(0);
    }
}

for ( my $i = 0 ; $i <= $#pid ; $i++ ) {
    waitpid( $pid[$i], 0 );
}
print "Done\n";

# Clean temporary files
print "Cleaning temporary files: ";
foreach $n ( 0 .. $nbproc - 1 ) {
    system("rm -f $scpn[$n]");
    system("rm -f $n.sh*");
}
print "Done\n";
