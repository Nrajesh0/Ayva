/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.focusbyrj.app.util.sync.supabase

/**
 * Configuration parameters for the Supabase Zero-Knowledge Cloud Vault.
 */
object SupabaseConfig {
    const val BASE_URL: String = "https://dndxklennzdctumznlpk.supabase.co"
    const val ANON_KEY: String = "sb_publishable_9bl6Ld9XdHUMt7O9WNnZWw_PWtdpyZa"

    // Auth endpoints
    const val AUTH_SIGNUP: String = "$BASE_URL/auth/v1/signup"
    const val AUTH_TOKEN: String = "$BASE_URL/auth/v1/token?grant_type=password"
    const val AUTH_REFRESH: String = "$BASE_URL/auth/v1/token?grant_type=refresh_token"
    const val AUTH_LOGOUT: String = "$BASE_URL/auth/v1/logout"
    const val AUTH_USER: String = "$BASE_URL/auth/v1/user"

    // Database REST endpoints for encrypted vault items (supports both table naming conventions)
    const val REST_VAULT_ITEMS: String = "$BASE_URL/rest/v1/vault_items"
    const val REST_SYNC_ITEMS: String = "$BASE_URL/rest/v1/sync_items"

    // Storage REST endpoints for encrypted binary media attachments
    const val STORAGE_BUCKET: String = "vault_media"
    const val STORAGE_OBJECT_URL: String = "$BASE_URL/storage/v1/object"

    /**
     * Recommended idempotent SQL to run in Supabase SQL Editor after deploying the Pattern 1 update.
     *
     * Schema changes from previous version:
     *   ADDED: wrapped_key    — CEK wrapped with user DEK (AES-GCM)
     *   ADDED: signature      — HMAC-SHA256(hmacKey, id:seq:isDeleted:ciphertext) anti-tamper tag
     *   ADDED: client_seq_num — monotonically increasing per-item client counter (replay prevention)
     *   CHANGED: type column is now nullable; new items write 'OPAQUE' (type metadata inside ciphertext)
     *   CHANGED: salt column is now nullable; new items write keyIv here
     *
     * Backward compatible: old items (type='NOTE'/'TASK') remain readable via legacy decryption path.
     */
    const val RECOMMENDED_SQL: String = """
-- ==============================================================================
-- 1. Create or update the unified vault_items table for Encrypted Notes & Tasks
--    Pattern 1: wrapped_key / signature / client_seq_num columns added.
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.vault_items (
    id TEXT NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE DEFAULT auth.uid(),
    type TEXT,
    ciphertext TEXT NOT NULL,
    salt TEXT,
    iv TEXT NOT NULL,
    wrapped_key TEXT,
    signature TEXT NOT NULL DEFAULT '',
    client_seq_num BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    updated_at BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    PRIMARY KEY (user_id, id)
);

-- Safe migrations for databases created before this schema version
ALTER TABLE public.vault_items ADD COLUMN IF NOT EXISTS wrapped_key TEXT;
ALTER TABLE public.vault_items ADD COLUMN IF NOT EXISTS signature TEXT NOT NULL DEFAULT '';
ALTER TABLE public.vault_items ADD COLUMN IF NOT EXISTS client_seq_num BIGINT NOT NULL DEFAULT 0;
ALTER TABLE public.vault_items ALTER COLUMN type DROP NOT NULL;
ALTER TABLE public.vault_items ALTER COLUMN salt DROP NOT NULL;

-- ==============================================================================
-- 2. Grant Table Permissions & Enable Row Level Security (RLS)
-- ==============================================================================
GRANT ALL ON TABLE public.vault_items TO postgres, anon, authenticated, service_role;

ALTER TABLE public.vault_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "vault_all_access" ON public.vault_items;
CREATE POLICY "vault_all_access" ON public.vault_items
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_vault_items_user_updated ON public.vault_items(user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_vault_items_user_seq ON public.vault_items(user_id, client_seq_num);

-- Compatibility alias for sync_items table if created in earlier migrations
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'sync_items') THEN
        GRANT ALL ON TABLE public.sync_items TO postgres, anon, authenticated, service_role;
        ALTER TABLE public.sync_items ENABLE ROW LEVEL SECURITY;
        DROP POLICY IF EXISTS "sync_items_all_access" ON public.sync_items;
        CREATE POLICY "sync_items_all_access" ON public.sync_items
            FOR ALL TO authenticated
            USING (auth.uid() = user_id)
            WITH CHECK (auth.uid() = user_id);
    END IF;
END $$;

-- ==============================================================================
-- 3. Storage Bucket for Zero-Knowledge Media Attachments (Photos & Audio Memos)
-- ==============================================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('vault_media', 'vault_media', false)
ON CONFLICT (id) DO NOTHING;

-- Enable RLS and permissions on storage.objects for the user's private media folder
DROP POLICY IF EXISTS "vault_media_authenticated_all" ON storage.objects;
CREATE POLICY "vault_media_authenticated_all" ON storage.objects
    FOR ALL
    TO authenticated
    USING (bucket_id = 'vault_media' AND (storage.foldername(name))[1] = auth.uid()::text)
    WITH CHECK (bucket_id = 'vault_media' AND (storage.foldername(name))[1] = auth.uid()::text);

-- ==============================================================================
-- 4. Enable Supabase Realtime (Optional / Push Notification Synchronization)
-- ==============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'vault_items'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.vault_items;
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- In case supabase_realtime publication is managed differently, ignore gracefully
    NULL;
END $$;
"""
}
