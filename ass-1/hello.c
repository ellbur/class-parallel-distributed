#include "mpi.h"
#include <stdio.h>

int main(int argc, char** argv)
{
  int MyProc, tag=0;
  char msg[12]="Hello World";
  char msg_recpt[12]="I am alone!";
  MPI_Status status;

  MPI_Init(&argc, &argv);
  MPI_Comm_rank(MPI_COMM_WORLD, &MyProc);

  printf("Process # %d started \n", MyProc);
  MPI_Barrier(MPI_COMM_WORLD);
 if (MyProc == 0)

  {
    printf("Proc #0: %s \n", msg_recpt) ;

    printf("Sending message to Proc #1: %s \n", msg) ;
    MPI_Send(&msg, 12, MPI_CHAR, 1, tag, MPI_COMM_WORLD);

    MPI_Recv(&msg_recpt, 12, MPI_CHAR, 1, tag, MPI_COMM_WORLD, &status);
    printf("Received message from Proc #1: %s \n", msg_recpt) ;
  }
  else
  {
    printf("Proc #1: %s \n", msg_recpt) ;

    MPI_Recv(&msg_recpt, 12, MPI_CHAR, 0, tag, MPI_COMM_WORLD, &status);
    printf("Received message from Proc #0: %s \n", msg_recpt) ;

    printf("Sending message to Proc #0: %s \n", msg) ;
    MPI_Send(&msg, 12, MPI_CHAR, 0, tag, MPI_COMM_WORLD);
  }

MPI_Finalize();
}

