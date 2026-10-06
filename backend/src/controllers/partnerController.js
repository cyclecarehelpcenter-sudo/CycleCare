const supabase = require('../config/supabase');
const crypto = require('crypto');
const { sendProductCampaignNotification } = require('../services/notificationService');

const ALL_PERMISSION_TYPES = [
  'CYCLE_WINDOW',
  'CARE_KIT',
  'WISHLIST',
  'SYMPTOMS',
  'MOOD',
  'REMINDERS',
  'SHOPPING',
  'DELIVERY_ADDRESS'
];

// Helper: Verify active connection and permission
async function verifyPartnerPermission(userId, connectionId, permissionType) {
  // Find connection
  const { data: conn, error: connErr } = await supabase
    .from('partner_connections')
    .select('*')
    .eq('id', connectionId)
    .single();

  if (connErr || !conn) {
    return { allowed: false, status: 404, message: 'Connection not found' };
  }

  if (conn.status !== 'ACCEPTED') {
    return { allowed: false, status: 403, message: `Connection is ${conn.status.toLowerCase()}` };
  }

  // Must be a participant in connection
  if (conn.requester_id !== userId && conn.recipient_id !== userId) {
    return { allowed: false, status: 403, message: 'Unauthorized connection access' };
  }

  // Determine who owns the data being shared
  // By default in CycleCare flow: recipient shares with requester or vice-versa
  const ownerId = conn.recipient_id === userId ? conn.requester_id : conn.recipient_id;

  if (!permissionType) {
    return { allowed: true, connection: conn, ownerId };
  }

  // Check permission table
  const { data: perm, error: permErr } = await supabase
    .from('partner_permissions')
    .select('enabled')
    .eq('connection_id', connectionId)
    .eq('permission_type', permissionType)
    .single();

  if (permErr || !perm || !perm.enabled) {
    return {
      allowed: false,
      status: 403,
      message: `Access denied. Partner has not granted permission for ${permissionType}.`
    };
  }

  return { allowed: true, connection: conn, ownerId };
}

// 1. Privacy-Safe Search (Never reveals cycle/health data!)
const searchPartner = async (req, res, next) => {
  try {
    let { cyclecare_id } = req.query;
    if (!cyclecare_id) {
      return res.status(400).json({ success: false, message: 'cyclecare_id is required' });
    }

    cyclecare_id = cyclecare_id.replace(/^@/, '').trim().toLowerCase();

    // Query user and basic profile ONLY
    const { data: user, error } = await supabase
      .from('users')
      .select('id, cyclecare_id, profiles(display_name)')
      .eq('cyclecare_id', cyclecare_id)
      .single();

    if (error || !user) {
      return res.status(404).json({ success: false, message: 'No user found with that CycleCare ID' });
    }

    // Do NOT return self
    if (user.id === req.user.id) {
      return res.status(400).json({ success: false, message: 'Cannot connect with yourself' });
    }

    res.json({
      success: true,
      partner: {
        id: user.id,
        cyclecare_id: `@${user.cyclecare_id}`,
        display_name: user.profiles?.display_name || 'CycleCare User'
      }
    });
  } catch (err) {
    next(err);
  }
};

// 2. Generate Secure Invite Link
const createInvite = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const token = crypto.randomBytes(16).toString('hex');
    const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000); // 7 days

    const { data: invite, error } = await supabase
      .from('partner_invites')
      .insert([{
        owner_user_id: userId,
        invite_token: token,
        expires_at: expiresAt
      }])
      .select()
      .single();

    if (error) throw error;

    res.json({
      success: true,
      invite_token: token,
      invite_url: `cyclecare://connect/${token}`,
      expires_at: expiresAt
    });
  } catch (err) {
    next(err);
  }
};

// 3. Validate Invite Token
const validateInvite = async (req, res, next) => {
  try {
    const { token } = req.params;

    const { data: invite, error } = await supabase
      .from('partner_invites')
      .select('*, users(cyclecare_id, profiles(display_name))')
      .eq('invite_token', token)
      .single();

    if (error || !invite) {
      return res.status(404).json({ success: false, message: 'Invalid invite link' });
    }

    if (new Date(invite.expires_at) < new Date() || invite.used_at) {
      return res.status(410).json({ success: false, message: 'Invite link has expired or has already been used' });
    }

    res.json({
      success: true,
      invite: {
        owner_id: invite.owner_user_id,
        cyclecare_id: invite.users?.cyclecare_id ? `@${invite.users.cyclecare_id}` : null,
        display_name: invite.users?.profiles?.display_name || 'CycleCare User'
      }
    });
  } catch (err) {
    next(err);
  }
};

// 4. Send Connection Request
const sendConnectionRequest = async (req, res, next) => {
  try {
    const requesterId = req.user.id;
    let { recipient_id, cyclecare_id, invite_token } = req.body;

    // Resolve recipient ID if not directly provided
    if (!recipient_id && cyclecare_id) {
      const cleanId = cyclecare_id.replace(/^@/, '').trim().toLowerCase();
      const { data: u } = await supabase.from('users').select('id').eq('cyclecare_id', cleanId).single();
      if (u) recipient_id = u.id;
    } else if (!recipient_id && invite_token) {
      const { data: inv } = await supabase.from('partner_invites').select('owner_user_id').eq('invite_token', invite_token).single();
      if (inv) recipient_id = inv.owner_user_id;
    }

    if (!recipient_id) {
      return res.status(400).json({ success: false, message: 'Recipient partner not found' });
    }

    if (recipient_id === requesterId) {
      return res.status(400).json({ success: false, message: 'Cannot connect to your own account' });
    }

    // Check existing connection
    const { data: existing } = await supabase
      .from('partner_connections')
      .select('*')
      .or(`and(requester_id.eq.${requesterId},recipient_id.eq.${recipient_id}),and(requester_id.eq.${recipient_id},recipient_id.eq.${requesterId})`)
      .single();

    if (existing) {
      if (existing.status === 'ACCEPTED') {
        return res.status(400).json({ success: false, message: 'You are already connected with this partner' });
      }
      if (existing.status === 'PENDING') {
        return res.status(400).json({ success: false, message: 'A connection request is already pending' });
      }
      if (existing.status === 'BLOCKED') {
        return res.status(403).json({ success: false, message: 'Unable to connect with this user' });
      }
      // If was revoked or declined, reopen
      const { data: renewed, error: renewErr } = await supabase
        .from('partner_connections')
        .update({ status: 'PENDING', requester_id: requesterId, recipient_id, updated_at: new Date() })
        .eq('id', existing.id)
        .select()
        .single();
      if (renewErr) throw renewErr;
      return res.json({ success: true, message: 'Partner request sent successfully', connection: renewed });
    }

    const { data: conn, error } = await supabase
      .from('partner_connections')
      .insert([{
        requester_id: requesterId,
        recipient_id: recipient_id,
        status: 'PENDING'
      }])
      .select()
      .single();

    if (error) throw error;

    // Send privacy-safe alert to recipient
    try {
      await supabase.from('notifications').insert([{
        user_id: recipient_id,
        title: 'New Partner Connection Request',
        body: 'Someone would like to connect with you on CycleCare.',
        type: 'PARTNER_REQUEST'
      }]);
    } catch (_) {}

    res.status(201).json({ success: true, message: 'Partner connection request sent', connection: conn });
  } catch (err) {
    next(err);
  }
};

// 5. List Requests and Connections
const listRequests = async (req, res, next) => {
  try {
    const userId = req.user.id;

    const { data: connections, error } = await supabase
      .from('partner_connections')
      .select('*, requester:requester_id(id, cyclecare_id, profiles(display_name)), recipient:recipient_id(id, cyclecare_id, profiles(display_name))')
      .or(`requester_id.eq.${userId},recipient_id.eq.${userId}`)
      .order('updated_at', { ascending: false });

    if (error) throw error;

    const incoming = [];
    const outgoing = [];
    const active = [];

    (connections || []).forEach(c => {
      const isRecipient = c.recipient_id === userId;
      const otherUser = isRecipient ? c.requester : c.recipient;
      const connData = {
        id: c.id,
        status: c.status,
        created_at: c.created_at,
        partner: {
          id: otherUser?.id,
          cyclecare_id: otherUser?.cyclecare_id ? `@${otherUser.cyclecare_id}` : null,
          display_name: otherUser?.profiles?.display_name || 'Partner'
        }
      };

      if (c.status === 'ACCEPTED') {
        active.push(connData);
      } else if (c.status === 'PENDING') {
        if (isRecipient) incoming.push(connData);
        else outgoing.push(connData);
      }
    });

    res.json({
      success: true,
      active,
      incoming,
      outgoing
    });
  } catch (err) {
    next(err);
  }
};

// 6. Accept Connection Request (Default permissions: ALL OFF)
const acceptRequest = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.id;

    const { data: conn, error: fetchErr } = await supabase
      .from('partner_connections')
      .select('*')
      .eq('id', connectionId)
      .single();

    if (fetchErr || !conn) {
      return res.status(404).json({ success: false, message: 'Request not found' });
    }

    if (conn.recipient_id !== userId) {
      return res.status(403).json({ success: false, message: 'Only the recipient can accept this request' });
    }

    // Update connection status
    const { data: updated, error } = await supabase
      .from('partner_connections')
      .update({ status: 'ACCEPTED', accepted_at: new Date(), updated_at: new Date() })
      .eq('id', connectionId)
      .select()
      .single();

    if (error) throw error;

    // Initialize all 8 granular permissions to FALSE (Default: ALL OFF)
    const permissionRows = ALL_PERMISSION_TYPES.map(type => ({
      connection_id: connectionId,
      permission_type: type,
      enabled: false
    }));

    await supabase.from('partner_permissions').upsert(permissionRows, { onConflict: 'connection_id, permission_type' });

    // Notify requester
    try {
      await supabase.from('notifications').insert([{
        user_id: conn.requester_id,
        title: 'Partner Connection Accepted!',
        body: 'You are now connected on CycleCare. Your partner can now choose what information to share.',
        type: 'PARTNER_ACCEPTED'
      }]);
    } catch (_) {}

    res.json({
      success: true,
      message: 'Connection accepted. All sharing permissions default to OFF.',
      connection: updated
    });
  } catch (err) {
    next(err);
  }
};

// 7. Decline Connection Request
const declineRequest = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.id;

    const { data: updated, error } = await supabase
      .from('partner_connections')
      .update({ status: 'DECLINED', declined_at: new Date(), updated_at: new Date() })
      .eq('id', connectionId)
      .eq('recipient_id', userId)
      .select()
      .single();

    if (error) throw error;
    res.json({ success: true, message: 'Connection request declined' });
  } catch (err) {
    next(err);
  }
};

// 8. Disconnect / Revoke Connection
const revokeConnection = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const { data: updated, error } = await supabase
      .from('partner_connections')
      .update({ status: 'REVOKED', revoked_at: new Date(), updated_at: new Date() })
      .eq('id', connectionId)
      .or(`requester_id.eq.${userId},recipient_id.eq.${userId}`)
      .select()
      .single();

    if (error) throw error;

    // Immediately disable all permissions
    await supabase.from('partner_permissions').update({ enabled: false }).eq('connection_id', connectionId);

    res.json({ success: true, message: 'Partner connection disconnected and access revoked.' });
  } catch (err) {
    next(err);
  }
};

// 9. Block Partner
const blockPartner = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const { data: updated, error } = await supabase
      .from('partner_connections')
      .update({ status: 'BLOCKED', updated_at: new Date() })
      .eq('id', connectionId)
      .or(`requester_id.eq.${userId},recipient_id.eq.${userId}`)
      .select()
      .single();

    if (error) throw error;
    await supabase.from('partner_permissions').update({ enabled: false }).eq('connection_id', connectionId);

    res.json({ success: true, message: 'User blocked' });
  } catch (err) {
    next(err);
  }
};

// 10. Get Permissions Matrix
const getPermissions = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const authCheck = await verifyPartnerPermission(userId, connectionId, null);
    if (!authCheck.allowed) {
      return res.status(authCheck.status).json({ success: false, message: authCheck.message });
    }

    const { data: perms, error } = await supabase
      .from('partner_permissions')
      .select('permission_type, enabled, updated_at')
      .eq('connection_id', connectionId);

    if (error) throw error;

    // Format as convenient key-value map
    const permissionMap = {};
    ALL_PERMISSION_TYPES.forEach(t => { permissionMap[t] = false; });
    (perms || []).forEach(p => { permissionMap[p.permission_type] = p.enabled; });

    res.json({
      success: true,
      connection_id: connectionId,
      permissions: permissionMap,
      raw: perms || []
    });
  } catch (err) {
    next(err);
  }
};

// 11. Update Permissions (Immediate Effect)
const updatePermissions = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;
    const { permissions } = req.body; // e.g. { CYCLE_WINDOW: true, CARE_KIT: false, ... }

    if (!permissions || typeof permissions !== 'object') {
      return res.status(400).json({ success: false, message: 'Permissions object required' });
    }

    const authCheck = await verifyPartnerPermission(userId, connectionId, null);
    if (!authCheck.allowed) {
      return res.status(authCheck.status).json({ success: false, message: authCheck.message });
    }

    // Upsert updated values
    const updates = [];
    for (const [permType, isEnabled] of Object.entries(permissions)) {
      if (ALL_PERMISSION_TYPES.includes(permType)) {
        updates.push({
          connection_id: connectionId,
          permission_type: permType,
          enabled: !!isEnabled,
          updated_at: new Date()
        });
      }
    }

    if (updates.length > 0) {
      const { error } = await supabase
        .from('partner_permissions')
        .upsert(updates, { onConflict: 'connection_id, permission_type' });
      if (error) throw error;
    }

    res.json({ success: true, message: 'Sharing settings updated successfully', updated: permissions });
  } catch (err) {
    next(err);
  }
};

// 12. Permitted Data Access: Shared Cycle Window (Requires CYCLE_WINDOW=ON)
const getSharedCycle = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const check = await verifyPartnerPermission(userId, connectionId, 'CYCLE_WINDOW');
    if (!check.allowed) {
      return res.status(check.status).json({ success: false, message: check.message });
    }

    const partnerId = check.ownerId;

    // Fetch cycle settings
    const { data: settings } = await supabase
      .from('cycle_settings')
      .select('cycle_length, period_duration')
      .eq('user_id', partnerId)
      .single();

    // Fetch latest period log
    const { data: latestPeriod } = await supabase
      .from('period_logs')
      .select('start_date, end_date')
      .eq('user_id', partnerId)
      .order('start_date', { ascending: false })
      .limit(1)
      .single();

    if (!latestPeriod) {
      return res.json({
        success: true,
        shared_cycle: {
          has_data: false,
          message: 'Partner has not logged cycle data yet.'
        }
      });
    }

    const cycleLength = settings?.cycle_length || 28;
    const periodDuration = settings?.period_duration || 5;
    const lastStart = new Date(latestPeriod.start_date);
    const nextEstimatedStart = new Date(lastStart.getTime() + cycleLength * 24 * 60 * 60 * 1000);
    const nextEstimatedEnd = new Date(nextEstimatedStart.getTime() + (periodDuration - 1) * 24 * 60 * 60 * 1000);

    const now = new Date();
    const daysUntilNext = Math.ceil((nextEstimatedStart - now) / (1000 * 60 * 60 * 24));

    res.json({
      success: true,
      shared_cycle: {
        has_data: true,
        estimated_window_start: nextEstimatedStart.toISOString().split('T')[0],
        estimated_window_end: nextEstimatedEnd.toISOString().split('T')[0],
        days_until_window: daysUntilNext > 0 ? daysUntilNext : 0,
        support_tip: daysUntilNext <= 3
          ? 'Preparation window is approaching. Perfect time to prepare comfort care and essentials!'
          : 'Normal cycle support window active.',
        disclaimer: 'Predictions are estimates for care preparation only, not medical diagnosis.'
      }
    });
  } catch (err) {
    next(err);
  }
};

// 13. Permitted Data Access: Shared Care Kit (Requires CARE_KIT=ON)
const getSharedCareKit = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const check = await verifyPartnerPermission(userId, connectionId, 'CARE_KIT');
    if (!check.allowed) {
      return res.status(check.status).json({ success: false, message: check.message });
    }

    const partnerId = check.ownerId;
    const { data: kits, error } = await supabase
      .from('care_kits')
      .select('*, care_kit_items(*, products(*))')
      .eq('user_id', partnerId);

    if (error) throw error;
    res.json({ success: true, care_kits: kits || [] });
  } catch (err) {
    next(err);
  }
};

// 14. Permitted Data Access: Shared Wishlist (Requires WISHLIST=ON)
const getSharedWishlist = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const check = await verifyPartnerPermission(userId, connectionId, 'WISHLIST');
    if (!check.allowed) {
      return res.status(check.status).json({ success: false, message: check.message });
    }

    const partnerId = check.ownerId;
    const { data: wishlist, error } = await supabase
      .from('wishlist')
      .select('*, products(*)')
      .eq('user_id', partnerId);

    if (error) throw error;
    res.json({ success: true, wishlist: wishlist || [] });
  } catch (err) {
    next(err);
  }
};

// 15. Create Care Package Order
const createCarePackage = async (req, res, next) => {
  try {
    const buyerId = req.user.id;
    const connectionId = req.params.connectionId;
    const { items, message, delivery_address } = req.body;

    const check = await verifyPartnerPermission(buyerId, connectionId, 'SHOPPING');
    if (!check.allowed) {
      return res.status(check.status).json({ success: false, message: check.message });
    }

    const recipientId = check.ownerId;

    if (!items || !Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ success: false, message: 'Care package must contain items' });
    }

    let totalAmount = 0;
    items.forEach(it => {
      totalAmount += (it.price || 199) * (it.quantity || 1);
    });

    // Create Order with buyer_id and recipient_user_id
    const { data: order, error } = await supabase
      .from('orders')
      .insert([{
        user_id: buyerId,
        buyer_id: buyerId,
        recipient_user_id: recipientId,
        total_amount: totalAmount,
        status: 'PAID',
        care_package_message: message || "Thinking of you. Take care! ❤️",
        delivery_address: delivery_address || 'Partner Saved Address'
      }])
      .select()
      .single();

    if (error) throw error;

    // Send supportive notification to recipient
    try {
      await supabase.from('notifications').insert([{
        user_id: recipientId,
        title: 'Care Package Sent To You! 🎁',
        body: `Your partner sent you a care package: "${message || 'Take care. I am here for you.'}"`,
        type: 'CARE_PACKAGE_RECEIVED'
      }]);
    } catch (_) {}

    res.status(201).json({
      success: true,
      message: 'Care package ordered successfully!',
      order
    });
  } catch (err) {
    next(err);
  }
};

// 16. Permitted Data Access: Shared Address (Requires DELIVERY_ADDRESS=ON)
const getSharedAddress = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const connectionId = req.params.connectionId;

    const check = await verifyPartnerPermission(userId, connectionId, 'DELIVERY_ADDRESS');
    if (!check.allowed) {
      return res.status(check.status).json({ success: false, message: check.message });
    }

    const partnerId = check.ownerId;
    const { data: addr, error } = await supabase
      .from('shared_addresses')
      .select('*')
      .eq('owner_user_id', partnerId)
      .eq('is_active', true)
      .limit(1)
      .single();

    res.json({ success: true, address: addr || null });
  } catch (err) {
    next(err);
  }
};

module.exports = {
  searchPartner,
  createInvite,
  validateInvite,
  sendConnectionRequest,
  listRequests,
  acceptRequest,
  declineRequest,
  revokeConnection,
  blockPartner,
  getPermissions,
  updatePermissions,
  getSharedCycle,
  getSharedCareKit,
  getSharedWishlist,
  createCarePackage,
  getSharedAddress
};
