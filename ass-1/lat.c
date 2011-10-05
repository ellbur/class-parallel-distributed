
#include "mpi.h"
#include <stdio.h>

#define tag 0

int ticks(void);

int main(int argc, char** argv)
{
    int this_proc;
    MPI_Status status;
    
    // Send and receive buffers
    char out[2] = "a";
    char in[2] = "a";
    int msg_len = 2;

    printf("Whatup\n");
    system("uname -a");
    
    MPI_Init(&argc, &argv);
    MPI_Comm_rank(MPI_COMM_WORLD, &this_proc);
    printf("Process # %d started \n", this_proc);
    
    MPI_Barrier(MPI_COMM_WORLD);
    
    if (this_proc == 0) {
        printf("%3d Sending...\n", ticks());
        MPI_Send(&out, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD);
        printf("%3d Receiving...\n", ticks());
        MPI_Recv(&msg_recpt, msg_len, MPI_CHAR, 1, tag, MPI_COMM_WORLD, &status);
    }
    else {
        MPI_Send(&out, msg_len, MPI_CHAR, 0, tag, MPI_COMM_WORLD);
        MPI_Recv(&msg_recpt, msg_len, MPI_CHAR, 0, tag, MPI_COMM_WORLD, &status);
    }
    
    MPI_Finalize();
}

int ticks(void) {
    return (int) (MPI_Wtime() / MPI_Wtick());
}

