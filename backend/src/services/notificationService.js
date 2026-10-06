const supabase = require('../config/supabase');
const { exec } = require('child_process');
const fs = require('fs');

async function sendProductCampaignNotification({
  productId,
  title,
  body,
  imageUrl,
  targetAudience = 'ALL'
}) {
  const deepLink = `cyclecare://product/${productId}`;

  // 1. Record campaign in Supabase database
  let campaign = null;
  try {
    const { data, error } = await supabase
      .from('notification_campaigns')
      .insert([{
        product_id: productId,
        title: title || 'New care essential available',
        body: body || 'Check out the latest addition to CycleCare Store.',
        image_url: imageUrl || null,
        deep_link: deepLink,
        target_audience: targetAudience,
        status: 'SENT',
        sent_at: new Date(),
        sent_count: 1
      }])
      .select()
      .single();

    if (!error) campaign = data;
  } catch (err) {
    console.error('Failed to save notification campaign:', err.message);
  }

  // 2. Insert into user notifications list
  try {
    await supabase.from('notifications').insert([{
      title: title || 'New care essential available',
      body: body || 'Check out the latest addition to CycleCare Store.',
      type: 'PRODUCT_ANNOUNCEMENT',
      metadata: {
        product_id: productId,
        deep_link: deepLink,
        image_url: imageUrl
      }
    }]);
  } catch (err) {
    console.error('Failed to insert user notification:', err.message);
  }

  // 3. Dispatch to connected device via ADB broadcast for real-time mobile push
  try {
    const adbPath = process.env.ADB_PATH || 'C:\\Users\\abdul\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe';
    if (fs.existsSync(adbPath)) {
      const cleanTitle = (title || 'New care essential available').replace(/"/g, '\\"');
      const cleanBody = (body || 'Check out the latest addition to CycleCare Store.').replace(/"/g, '\\"');
      const cleanImg = imageUrl ? imageUrl.replace(/"/g, '\\"') : '';
      const cmd = `"${adbPath}" shell am broadcast -a com.cyclecare.ACTION_STORE_PRODUCT_ADDED -p com.cyclecare --es name "${cleanTitle}" --es price "199" --es product_id "${productId}" --es image_url "${cleanImg}" --es deep_link "${deepLink}"`;
      exec(cmd, (err) => {
        if (!err) console.log('Dispatched local notification broadcast to phone');
      });
    }
  } catch (_) {}

  return {
    success: true,
    campaignId: campaign ? campaign.id : null,
    deepLink,
    title,
    body
  };
}

module.exports = {
  sendProductCampaignNotification
};
