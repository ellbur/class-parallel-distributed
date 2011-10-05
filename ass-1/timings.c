
#include "mpi.h"
#include <stdio.h>
#include <math.h>

#define tag 0

int ticks(void);
void reset_clock(void);

static double start = 0./0.;
static double tick_size = 0./0.;

// 1 MB (2**20 bytes)
#define MAX_MESSAGE_LEN 0x00100000

void run_trial(
    int trial_num,
    int msg_len,
    int num_iters,
    int comp_size
);
void run_comp(void);

int main(int argc, char** argv)
{
    int this_proc;
    MPI_Status status;
    
    // Send and receive buffers
    char out[2] = "a";
    char in[2] = "a";
    int msg_len = 2;
    
    int i;

    system("uname -a");
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &this_proc);
    
    MPI_Barrier(MPI_COMM_WORLD);
    reset_clock();
    
    if (this_proc == 0)
        printf("Clock tick is %.3e\n", tick_size);
    
    run_trial(0, 1<<0, 10, 0);
    if (this_proc==0) printf("\n");
    
    run_trial(1, 1<<1, 10, 0);
    if (this_proc==0) printf("\n");
    
    run_trial(2, 1<<2, 10, 0);
    if (this_proc==0) printf("\n");
    
    run_trial(3, 1<<3, 10, 0);
    if (this_proc==0) printf("\n");
    
    MPI_Finalize();
}

void run_trial(
    int trial_num,
    int msg_len,
    int num_iters,
    int comp_size
)
{
    static char msg[MAX_MESSAGE_LEN];
    MPI_Status status;
    int this_proc;
    volatile int i, j;
        
    MPI_Barrier(MPI_COMM_WORLD);
    MPI_Comm_rank(MPI_COMM_WORLD, &this_proc);
    
    if (this_proc == 0) {
        for (i=0; i<num_iters; i++) {
            printf("[trial %03d] %5d\n", trial_num, ticks());
            MPI_Send(&msg, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD);
            MPI_Recv(&msg, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD, &status);
            
            for (j=0; j<comp_size; j++)
                run_comp();
        }
    }
    else {
        for (i=0; i<num_iters; i++) {
            MPI_Send(&msg, msg_len, MPI_CHAR, 0, tag, MPI_COMM_WORLD);
            MPI_Recv(&msg, msg_len, MPI_CHAR, 0, tag, MPI_COMM_WORLD, &status);
        }
    }
}

void run_comp(void) {
    volatile double x = 7;
    volatile int i;
    
    for (i=0; i<100000; i++) {
        x = sin(cos(x));
    }
}

void reset_clock(void) {
    start = MPI_Wtime();
    tick_size = MPI_Wtick();
}

int ticks(void) {
    return (int) ((MPI_Wtime()-start) / tick_size);
}

