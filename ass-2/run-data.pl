#!/usr/bin/env perl

use Env qw(DRY);

`echo > all-data.txt`;

my @queue = ();

sub run {
    my ($cmd) = @_;
    push(@queue, $cmd);
}

sub process {
    my $N = $#queue+1;
    my $n = 1;
    
    for $cmd (@queue) {
        print "\033[34m[$n/$N]\033[0m \033[36m$cmd\n\033[0m";
        if (!$DRY) {
            `$cmd`;
        }
        $n++;
    }
}

@mpiprogs = ('./mpi-static', './mpi-dyn', './mpi-static-shuffle',
    './mpi-dyn-shuffle', './mpi-omp-static', './mpi-omp-dyn');

for $prog (@mpiprogs) {
    for $i (2 ... 14) {
        run("mpirun -n $i $prog >> all-data.txt");
    }
}

@ompprogs = ('./omp-static', './omp-dyn', './omp-static-shuffle',
    './omp-dyn-shuffle');

for $prog (@ompprogs) {
    for $i (2 ... 10) {
        run("OMP_NUM_THREADS=$i $prog >> all-data.txt");
    }
}

run("./mandelbrot-nodisp >> all-data.txt");

process()

