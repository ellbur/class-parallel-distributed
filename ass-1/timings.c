
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
    
    int i, j;
    int trial;
    double x;

    system("uname -a");
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &this_proc);
    
    MPI_Barrier(MPI_COMM_WORLD);
    reset_clock();
    
    if (this_proc == 0) {
        printf("Clock tick is %.3e\n\n", tick_size);
    }
    
    trial = 0;
    
    // No-computation trials
    for (x=0; x<=16; x += 0.5) {
        run_trial(trial++, (int)pow(2,x), 10, 0);
    }
    
    // Mostly computation trials
    for (i=0; i<20; i++) {
        run_trial(trial++, 1, 10, i);
    }
    
    // Mixed trials
    for (i=0; i<5; i++) {
        for (j=0; j<5; j++) {
            run_trial(trial++, 1<<(i*2), 10, j*5);
        }
    }
    
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
        printf("trial,msglen,compsize\n");
        printf("%03d,%07d,%03d\n",
            trial_num,
            msg_len,
            comp_size
        );
        printf("\n");
        
        printf("trial,iter,ticks\n");
        for (i=0; i<num_iters; i++) {
            if (i>0) {
                printf("%03d,%03d,%05d\n",
                    trial_num,
                    i,
                    ticks()
                );
            }
            reset_clock();
            
            MPI_Send(&msg, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD);
            MPI_Recv(&msg, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD, &status);
            
            for (j=0; j<comp_size; j++)
                run_comp();
        }
        
        printf("\n");
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
    
    for (i=0; i<10000; i++) {
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

