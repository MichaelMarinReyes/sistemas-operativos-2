#define _DEFAULT_SOURCE
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>

#define FIFO_PATH "/tmp/fifo_pagos"
#define MAX_BUFFER 512

void limpiarBufferStdin(void) {
    int c;
    while ((c = getchar()) != '\n' && c != EOF);
}

int main(void) {
    int opcion = 0;

    while (1) {
        printf("\n======================================================================\n");
        printf("                 TERMINAL DE SUCURSAL (sucursal.c)                    \n");
        printf("======================================================================\n");
        printf("  1. Enviar reporte diario de pagos al Centro de Operaciones\n");
        printf("  2. Enviar comando 'cerrar' para finalizar la recepcion general\n");
        printf("  3. Salir\n");
        printf("======================================================================\n");
        printf("Seleccione una opcion (1-3): ");

        while (scanf("%d", &opcion) != 1 || (opcion < 1 || opcion > 3)) {
            printf("[!] Opcion invalida. Ingrese un valor entre 1 y 3: ");
            limpiarBufferStdin();
        }
        limpiarBufferStdin();

        if (opcion == 3) {
            printf("\nSaliendo de la terminal de sucursal...\n");
            break;
        }

        char mensajeEnviar[MAX_BUFFER];

        if (opcion == 1) {
            char nombreSucursal[128];
            printf("\nIngrese el nombre o identificador de la sucursal: ");
            if (fgets(nombreSucursal, sizeof(nombreSucursal), stdin) == NULL) {
                strcpy(nombreSucursal, "Sucursal_Desconocida");
            }
            nombreSucursal[strcspn(nombreSucursal, "\r\n")] = '\0';

            double monto = 0.0;
            while (1) {
                printf("Ingrese el monto total de pagos procesados hoy (Q): ");
                if (scanf("%lf", &monto) == 1 && monto >= 0.0) {
                    limpiarBufferStdin();
                    break;
                }
                printf("[!] Error: Ingrese un valor numerico valido mayor o igual a 0.\n");
                limpiarBufferStdin();
            }

            snprintf(mensajeEnviar, sizeof(mensajeEnviar), "%s|%.2f", nombreSucursal, monto);
        } else if (opcion == 2) {
            strcpy(mensajeEnviar, "cerrar");
        }

        printf("\n[*] Conectando con el Centro de Operaciones (%s)...\n", FIFO_PATH);

        // Asegurar que la FIFO exista para que los programas puedan iniciarse en cualquier orden
        if (mkfifo(FIFO_PATH, 0666) == -1 && errno != EEXIST) {
            perror("[!] Advertencia al verificar FIFO con mkfifo()");
        }

        // Abrir la FIFO en modo solo escritura (bloquea hasta que centro.c la abra para lectura)
        int fd = open(FIFO_PATH, O_WRONLY);
        if (fd == -1) {
            fprintf(stderr, "[!] Error al conectar con la FIFO (%s): %s\n", FIFO_PATH, strerror(errno));
            fprintf(stderr, "[!] Asegurese de que el Centro de Operaciones este activo.\n");
            continue;
        }

        ssize_t bytesEscritos = write(fd, mensajeEnviar, strlen(mensajeEnviar));
        close(fd);

        if (bytesEscritos == -1) {
            perror("[!] Error al transmitir datos por la FIFO");
            continue;
        }

        printf("[+] Transmision completada con exito:\n");
        printf("    Contenido     : \"%s\"\n", mensajeEnviar);
        printf("    Bytes enviados: %ld\n", (long)bytesEscritos);
        printf("======================================================================\n");
    }

    return 0;
}
