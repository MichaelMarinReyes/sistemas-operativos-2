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
#include <time.h>
#include <errno.h>

#define FIFO_PATH "/tmp/fifo_pagos"
#define MAX_BUFFER 512

int main(void) {
    printf("======================================================================\n");
    printf("            CENTRO DE OPERACIONES - TELEFONIA (centro.c)              \n");
    printf("======================================================================\n");

    // 1. Crear el archivo especial FIFO en el sistema de archivos si no existe
    if (mkfifo(FIFO_PATH, 0666) == -1) {
        if (errno != EEXIST) {
            perror("[!] Error al crear la FIFO con mkfifo()");
            exit(EXIT_FAILURE);
        }
    }

    printf("[*] Archivo FIFO establecido en: %s\n", FIFO_PATH);
    printf("[*] Servidor iniciado. Esperando reportes de sucursales...\n");
    printf("[*] Para finalizar el turno, envie 'cerrar' desde sucursal.c\n");
    printf("----------------------------------------------------------------------\n");

    // Abrimos en modo O_RDWR para evitar que read() retorne 0 (EOF) inmediatamente
    // cada vez que una sucursal finaliza y cierra su extremo de escritura.
    int fd = open(FIFO_PATH, O_RDWR);
    if (fd == -1) {
        perror("[!] Error al abrir la FIFO en modo lectura/escritura");
        exit(EXIT_FAILURE);
    }

    char buffer[MAX_BUFFER];
    double totalGeneralPagos = 0.0;
    int cantidadReportes = 0;

    while (1) {
        memset(buffer, 0, sizeof(buffer));
        ssize_t bytesLeidos = read(fd, buffer, sizeof(buffer) - 1);

        if (bytesLeidos > 0) {
            buffer[bytesLeidos] = '\0';
            buffer[strcspn(buffer, "\r\n")] = '\0';

            // Obtener fecha y hora exacta del sistema (timestamp)
            time_t tiempoActual = time(NULL);
            struct tm *infoTiempo = localtime(&tiempoActual);
            char horaFormateada[32];
            strftime(horaFormateada, sizeof(horaFormateada), "%Y-%m-%d %H:%M:%S", infoTiempo);

            // Comprobar mensaje de finalización
            if (strcasecmp(buffer, "cerrar") == 0) {
                printf("\n[%s] >>> MENSAJE DE CIERRE RECIBIDO <<<\n", horaFormateada);
                break;
            }

            // Procesar el reporte recibido: SUCURSAL|MONTO
            char copiaBuffer[MAX_BUFFER];
            strncpy(copiaBuffer, buffer, sizeof(copiaBuffer) - 1);
            copiaBuffer[sizeof(copiaBuffer) - 1] = '\0';

            char *tokenSucursal = strtok(copiaBuffer, "|");
            char *tokenMonto = strtok(NULL, "|");

            if (tokenSucursal && tokenMonto) {
                double monto = atof(tokenMonto);
                totalGeneralPagos += monto;
                cantidadReportes++;

                printf("[%s] Reporte #%02d recibido:\n", horaFormateada, cantidadReportes);
                printf("       Sucursal       : %s\n", tokenSucursal);
                printf("       Monto reportado: Q. %.2f\n", monto);
                printf("       Total acumulado: Q. %.2f\n", totalGeneralPagos);
                printf("----------------------------------------------------------------------\n");
            } else {
                printf("[%s] Mensaje recibido con formato no estandar: \"%s\"\n", horaFormateada, buffer);
            }
        }
    }

    // Cerrar descriptor y eliminar la FIFO del sistema de archivos
    close(fd);
    unlink(FIFO_PATH);

    printf("\n======================================================================\n");
    printf("               RESUMEN CONSOLIDADO AL CIERRE DEL DIA                  \n");
    printf("======================================================================\n");
    printf("  Total de reportes recibidos      : %d\n", cantidadReportes);
    printf("  Monto total de pagos del dia     : Q. %.2f\n", totalGeneralPagos);
    printf("  Estado                           : FIFO cerrada y removida exitosamente.\n");
    printf("======================================================================\n");

    return 0;
}
