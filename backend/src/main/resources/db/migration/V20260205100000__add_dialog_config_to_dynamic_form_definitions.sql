-- Migration: Add dialog_config column to dynamic_form_definitions table
-- Purpose: Allow configurable dialog sizes per program/entity
-- Created: 2026-02-05

ALTER TABLE dynamic_form_definitions
ADD COLUMN IF NOT EXISTS dialog_config JSONB DEFAULT '{
  "width": "800px",
  "maxWidth": "95vw",
  "maxHeight": "90vh",
  "fullscreenMobile": true
}'::jsonb;

-- Add comment to explain the column purpose
COMMENT ON COLUMN dynamic_form_definitions.dialog_config IS 'Configuration for dialog size and behavior (width, maxWidth, maxHeight, fullscreenMobile)';
