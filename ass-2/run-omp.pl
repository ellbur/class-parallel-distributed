#!/usr/bin/env perl

use Env qw(DRY);

$target = 'omp-data.txt';

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

@ompprogs = ('./omp-static', './omp-dyn', './omp-static-shuffle',
    './omp-dyn-shuffle');

for $prog (@ompprogs) {
    for $i (2 ... 30) {
        run("OMP_NUM_THREADS=$i $prog >> $target");
    }
}

run("./mandelbrot-nodisp >> all-data.txt");

process()


