-- ============================================================
-- Rastro de autoría en las entidades de negocio.
--
-- Hasta aquí solo 'cliente' guardaba datos de registro, de modo que no
-- había forma de responder quién anuló una factura o quién modificó un
-- contrato. Los rellena Spring Data mediante EntidadAuditable y el
-- AuditorAware de AuditoriaConfig.
--
-- El tipo es timestamp(6) without time zone, que es el que Hibernate
-- genera para LocalDateTime: con cualquier otro, ddl-auto=validate
-- rechazaría el arranque.
--
-- Las filas existentes quedan con las cuatro columnas nulas a propósito.
-- Rellenarlas con un autor inventado seria peor que dejarlas vacias: un
-- nulo dice "anterior a la auditoria", que es la verdad.
-- ============================================================

ALTER TABLE public.cliente
    ADD COLUMN creado_por      character varying(60),
    ADD COLUMN creado_en       timestamp(6) without time zone,
    ADD COLUMN modificado_por  character varying(60),
    ADD COLUMN modificado_en   timestamp(6) without time zone;

ALTER TABLE public.servicio
    ADD COLUMN creado_por      character varying(60),
    ADD COLUMN creado_en       timestamp(6) without time zone,
    ADD COLUMN modificado_por  character varying(60),
    ADD COLUMN modificado_en   timestamp(6) without time zone;

ALTER TABLE public.contrato
    ADD COLUMN creado_por      character varying(60),
    ADD COLUMN creado_en       timestamp(6) without time zone,
    ADD COLUMN modificado_por  character varying(60),
    ADD COLUMN modificado_en   timestamp(6) without time zone;

ALTER TABLE public.proyecto
    ADD COLUMN creado_por      character varying(60),
    ADD COLUMN creado_en       timestamp(6) without time zone,
    ADD COLUMN modificado_por  character varying(60),
    ADD COLUMN modificado_en   timestamp(6) without time zone;

ALTER TABLE public.factura
    ADD COLUMN creado_por      character varying(60),
    ADD COLUMN creado_en       timestamp(6) without time zone,
    ADD COLUMN modificado_por  character varying(60),
    ADD COLUMN modificado_en   timestamp(6) without time zone;
