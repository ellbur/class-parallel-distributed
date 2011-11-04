#!/usr/bin/env perl

use Env qw(DRY);

$target = 'mpi-data.txt';

`echo > $target`;

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
    './mpi-dyn-shuffle');

for $prog (@mpiprogs) {
    for $i (2 ... 14) {
        run("mpirun -n $i $prog >> $target");
    }
}

run("./mandelbrot-nodisp >> $target");

process()

