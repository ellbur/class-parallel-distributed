
#include "mpi.h"
#include <stdio.h>

#define tag 0

int ticks(void);
void reset_clock(void);

static double start = 0./0.;
static double tick_size = 0./0.;

int main(int argc, char** argv)
{
    int this_proc;
    MPI_Status status;
    
    // Send and receive buffers
    char out[2] = "a";
    char in[2] = "a";
    int msg_len = 2;
    
    int i;

    printf("Whatup\n");
    system("uname -a");
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &this_proc);
    printf("Process # %d started \n", this_proc);
    
    MPI_Barrier(MPI_COMM_WORLD);
    
    reset_clock();
    
    for (i=0; i<20; i++) {
        printf("%d %d\n", this_proc, ticks());
    }
    
    MPI_Finalize();
}

void reset_clock(void) {
    start = MPI_Wtime();
    tick_size = MPI_Wtick();
}

int ticks(void) {
    return (int) ((MPI_Wtime()-start) / tick_size);
}

