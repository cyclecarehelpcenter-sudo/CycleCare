const { createClient } = require('@supabase/supabase-js');
require('dotenv').config();

let supabaseUrl = process.env.SUPABASE_URL;
if (!supabaseUrl || !supabaseUrl.startsWith('http') || supabaseUrl.includes('YOUR_SUPABASE_URL')) {
  supabaseUrl = 'https://zbgzrkdmvtofyikksptq.supabase.co';
}

const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY || 'mock-service-key';

const supabase = createClient(supabaseUrl, supabaseKey);

module.exports = supabase;
