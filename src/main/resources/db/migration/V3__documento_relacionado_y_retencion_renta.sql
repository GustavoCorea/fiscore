-- ============================================================
-- Dos campos que el esquema de Hacienda exige y el modelo no tenía.
--
-- factura_relacionada_id: el documento que corrige una nota de crédito (05)
--   o de débito (06). Sin esa referencia el documento se rechaza. Es una
--   autorreferencia sobre 'factura': la nota apunta al DTE original.
--
-- retencion_renta: la retención sobre honorarios profesionales. Las tasas
--   (RETENCION_RENTA_TASA, RETENCION_MONTO_MINIMO) llevaban desde el
--   principio en DTE_PARAMETRO sin que existiera dónde guardar el importe
--   calculado.
--
-- Las filas existentes quedan con retencion_renta nulo, que el servicio trata
-- como cero. No se rellenan a cero por no reescribir documentos ya emitidos:
-- un DTE no se toca después de emitido.
-- ============================================================

ALTER TABLE public.factura
    ADD COLUMN retencion_renta         numeric(38,2),
    ADD COLUMN factura_relacionada_id  bigint;

ALTER TABLE public.factura
    ADD CONSTRAINT fk_factura_relacionada
        FOREIGN KEY (factura_relacionada_id) REFERENCES public.factura(id);

-- Buscar "¿qué notas corrigen a este documento?" es la consulta natural sobre
-- esta columna, y sin índice recorre la tabla entera.
CREATE INDEX idx_factura_relacionada ON public.factura (factura_relacionada_id);
