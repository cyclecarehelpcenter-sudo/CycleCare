const supabase = require('../config/supabase');

// Curated Quick Care & Medical Essentials catalog for in-chat sharing
const QUICK_CARE_ITEMS = [
  {
    id: "a1111111-1111-1111-1111-111111111111",
    name: "CycleCare Organic Cotton Pads (Night)",
    category: "Period Care",
    price: 149,
    image_url: "https://images.unsplash.com/photo-1583947215259-38e31be8751f?auto=format&fit=crop&w=600&q=80",
    description: "Ultra-absorbent heavy flow night protection pads (10x)"
  },
  {
    id: "b1111111-1111-1111-1111-111111111111",
    name: "Instant Warmth Heat Patch (Pack of 3)",
    category: "Comfort & Cramps",
    price: 199,
    image_url: "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80",
    description: "Air-activated warming patches soothing pelvic cramps up to 8 hrs"
  },
  {
    id: "d2222222-2222-2222-2222-222222222222",
    name: "Chamomile & Ginger Soothing Herbal Tea",
    category: "Soothing Teas",
    price: 180,
    image_url: "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80",
    description: "Herbal blend easing bloating, spasm and body tension"
  },
  {
    id: "009f4b12-2efa-4e28-8c1a-4f872e955fb7",
    name: "CycleCare Herbal Cramp Relief Roll-On",
    category: "Medical & Pain Relief",
    price: 249,
    image_url: "https://images.unsplash.com/photo-1608571423902-eed4a5ad8108?auto=format&fit=crop&w=600&q=80",
    description: "Ayurvedic botanical cooling oil for fast abdominal pain ease"
  },
  {
    id: "d1111111-1111-1111-1111-111111111111",
    name: "CycleCare Dark Comfort Chocolate (70%)",
    category: "Comfort Treats",
    price: 120,
    image_url: "https://images.unsplash.com/photo-1549007994-cb92caebd54b?auto=format&fit=crop&w=600&q=80",
    description: "Rich dark chocolate with magnesium to ease muscle soreness"
  },
  {
    id: "carekit-premium-01",
    name: "CycleCare Emergency SOS Care Kit",
    category: "Emergency & Medical",
    price: 499,
    image_url: "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80",
    description: "Complete discreet kit with pads, heating patch, wipes & herbal tea"
  }
];

// 1. Get Circle Contacts (Husband, Wife, Parents, Partner, Admin Help, etc.)
const getContacts = async (req, res, next) => {
  try {
    const userId = req.user.id;

    // Fetch active partner connections
    const { data: connections, error } = await supabase
      .from('partner_connections')
      .select('*, requester:requester_id(id, cyclecare_id, email, profiles(display_name)), recipient:recipient_id(id, cyclecare_id, email, profiles(display_name))')
      .or(`requester_id.eq.${userId},recipient_id.eq.${userId}`)
      .eq('status', 'ACCEPTED')
      .order('updated_at', { ascending: false });

    if (error) throw error;

    const contacts = [];
    const connectedUserIds = new Set();

    for (const c of (connections || [])) {
      const isRecipient = c.recipient_id === userId;
      const otherUser = isRecipient ? c.requester : c.recipient;
      if (!otherUser) continue;

      connectedUserIds.add(otherUser.id);

      // Get latest message in this connection
      const { data: latestMsg } = await supabase
        .from('circle_messages')
        .select('*')
        .eq('connection_id', c.id)
        .order('created_at', { ascending: false })
        .limit(1)
        .single();

      // Count unread
      const { count: unreadCount } = await supabase
        .from('circle_messages')
        .select('*', { count: 'exact', head: true })
        .eq('connection_id', c.id)
        .eq('receiver_id', userId)
        .eq('is_read', false);

      const otherProfile = Array.isArray(otherUser.profiles) ? otherUser.profiles[0] : otherUser.profiles;
      const displayName = otherProfile?.display_name || otherUser.email?.split('@')[0] || 'Circle Partner';
      const relationshipTag = c.custom_relationship_label || c.relationship || 'Partner';

      contacts.push({
        connection_id: c.id,
        user_id: otherUser.id,
        display_name: displayName,
        cyclecare_id: otherUser.cyclecare_id ? `@${otherUser.cyclecare_id}` : `@${displayName.toLowerCase().replace(/\s+/g, '_')}`,
        relationship: relationshipTag,
        last_message: latestMsg ? latestMsg.content : 'Start a caring conversation...',
        last_message_type: latestMsg ? latestMsg.message_type : 'TEXT',
        last_message_time: latestMsg ? latestMsg.created_at : c.created_at,
        unread_count: unreadCount || 0
      });
    }

    // Ensure CycleCare Admin (Help) is always present for every user
    const { data: adminUser } = await supabase
      .from('users')
      .select('id, email, cyclecare_id, profiles(display_name)')
      .eq('email', 'admin@cyclecare.app')
      .single();

    if (adminUser && adminUser.id !== userId && !connectedUserIds.has(adminUser.id)) {
      // Auto-connect with Admin (Help)
      const { data: adminConn } = await supabase
        .from('partner_connections')
        .insert([{
          requester_id: userId,
          recipient_id: adminUser.id,
          relationship: 'Other',
          custom_relationship_label: 'Admin (Help)',
          status: 'ACCEPTED'
        }])
        .select('id, created_at')
        .single();

      if (adminConn) {
        contacts.unshift({
          connection_id: adminConn.id,
          user_id: adminUser.id,
          display_name: 'CycleCare Admin (Help)',
          cyclecare_id: '@admin_help',
          relationship: 'Admin (Help)',
          last_message: 'Hello! Official CycleCare support is here for you. How can we help?',
          last_message_type: 'TEXT',
          last_message_time: adminConn.created_at,
          unread_count: 0
        });
      }
    }

    // If user follows people who don't have connection yet, also include them
    const { data: follows } = await supabase
      .from('user_follows')
      .select('following_id, followed_user:following_id(id, email, cyclecare_id, profiles(display_name))')
      .eq('follower_id', userId);

    for (const f of (follows || [])) {
      if (!f.followed_user || connectedUserIds.has(f.following_id)) continue;
      
      const { data: newConn } = await supabase
        .from('partner_connections')
        .insert([{
          requester_id: userId,
          recipient_id: f.following_id,
          relationship: 'Other',
          custom_relationship_label: 'Friend',
          status: 'ACCEPTED'
        }])
        .select('id, created_at')
        .single();

      if (newConn) {
        connectedUserIds.add(f.following_id);
        const fProf = Array.isArray(f.followed_user.profiles) ? f.followed_user.profiles[0] : f.followed_user.profiles;
        const name = fProf?.display_name || f.followed_user.email?.split('@')[0] || 'Friend';
        contacts.push({
          connection_id: newConn.id,
          user_id: f.following_id,
          display_name: name,
          cyclecare_id: f.followed_user.cyclecare_id ? `@${f.followed_user.cyclecare_id}` : `@${name.toLowerCase().replace(/\s+/g, '_')}`,
          relationship: 'Friend',
          last_message: 'Connected on CycleCare! Say hello 🌸',
          last_message_type: 'TEXT',
          last_message_time: newConn.created_at,
          unread_count: 0
        });
      }
    }

    res.json({ success: true, contacts });
  } catch (err) {
    next(err);
  }
};

// 2. Get Messages for a Connection
const getMessages = async (req, res, next) => {
  try {
    const userId = req.user.id;
    let { connection_id } = req.params;

    if (!connection_id) {
      return res.status(400).json({ success: false, message: 'connection_id is required' });
    }

    // If demo connection id was passed by legacy client, resolve user's primary connection
    if (connection_id === 'demo-circle-connection') {
      const { data: primaryConn } = await supabase
        .from('partner_connections')
        .select('id')
        .or(`requester_id.eq.${userId},recipient_id.eq.${userId}`)
        .eq('status', 'ACCEPTED')
        .order('updated_at', { ascending: false })
        .limit(1)
        .single();
      if (primaryConn) {
        connection_id = primaryConn.id;
      }
    }

    const { data, error } = await supabase
      .from('circle_messages')
      .select('*')
      .eq('connection_id', connection_id)
      .order('created_at', { ascending: true });

    if (error) throw error;
    const messages = data || [];

    // Mark received messages as read
    await supabase
      .from('circle_messages')
      .update({ is_read: true })
      .eq('connection_id', connection_id)
      .eq('receiver_id', userId)
      .eq('is_read', false);

    res.json({
      success: true,
      connection_id,
      messages: messages.map(m => ({
        ...m,
        is_mine: m.sender_id === userId
      }))
    });
  } catch (err) {
    next(err);
  }
};

// 3. Send Text Message
const sendMessage = async (req, res, next) => {
  try {
    const senderId = req.user.id;
    let { connection_id, receiver_id, content, message_type = 'TEXT' } = req.body;

    if (!content || !content.trim()) {
      return res.status(400).json({ success: false, message: 'Message content cannot be empty' });
    }

    let targetReceiverId = receiver_id;
    let targetConnId = connection_id;

    // Resolve connection & receiver if not provided or demo id
    if (!targetConnId || targetConnId === 'demo-circle-connection') {
      if (targetReceiverId) {
        const { data: conn } = await supabase
          .from('partner_connections')
          .select('id')
          .or(`and(requester_id.eq.${senderId},recipient_id.eq.${targetReceiverId}),and(requester_id.eq.${targetReceiverId},recipient_id.eq.${senderId})`)
          .single();
        if (conn) {
          targetConnId = conn.id;
        } else {
          const { data: newConn } = await supabase
            .from('partner_connections')
            .insert([{
              requester_id: senderId,
              recipient_id: targetReceiverId,
              relationship: 'Other',
              custom_relationship_label: 'Partner',
              status: 'ACCEPTED'
            }])
            .select('id')
            .single();
          if (newConn) targetConnId = newConn.id;
        }
      } else {
        // Fallback to primary connection
        const { data: pConn } = await supabase
          .from('partner_connections')
          .select('id, requester_id, recipient_id')
          .or(`requester_id.eq.${senderId},recipient_id.eq.${senderId}`)
          .eq('status', 'ACCEPTED')
          .order('updated_at', { ascending: false })
          .limit(1)
          .single();
        if (pConn) {
          targetConnId = pConn.id;
          targetReceiverId = pConn.requester_id === senderId ? pConn.recipient_id : pConn.requester_id;
        }
      }
    } else if (!targetReceiverId) {
      const { data: conn } = await supabase.from('partner_connections').select('requester_id, recipient_id').eq('id', targetConnId).single();
      if (conn) {
        targetReceiverId = conn.requester_id === senderId ? conn.recipient_id : conn.requester_id;
      }
    }

    if (!targetReceiverId) {
      return res.status(400).json({ success: false, message: 'Could not determine receiver for message' });
    }

    const { data: newMsg, error } = await supabase
      .from('circle_messages')
      .insert([{
        connection_id: targetConnId,
        sender_id: senderId,
        receiver_id: targetReceiverId,
        message_type,
        content: content.trim(),
        metadata: {}
      }])
      .select()
      .single();

    if (error) throw error;

    // Push notification to partner
    try {
      await supabase.from('notifications').insert([{
        user_id: targetReceiverId,
        title: 'New Message from Circle',
        body: content.trim().length > 50 ? content.trim().substring(0, 47) + '...' : content.trim(),
        type: 'CIRCLE_MESSAGE'
      }]);
    } catch (_) {}

    res.status(201).json({
      success: true,
      message: { ...newMsg, is_mine: true }
    });
  } catch (err) {
    next(err);
  }
};

// 4. Send or Request Care / Medical Item in Chat
const sendCareItem = async (req, res, next) => {
  try {
    const senderId = req.user.id;
    let { 
      connection_id, 
      receiver_id, 
      item_name, 
      item_price, 
      item_category, 
      item_image, 
      is_request = false, 
      note 
    } = req.body;

    if (!item_name) {
      return res.status(400).json({ success: false, message: 'item_name is required' });
    }

    const messageType = is_request ? 'CARE_REQUEST' : 'CARE_ITEM_SENT';
    const content = note && note.trim().length > 0 
      ? note.trim() 
      : (is_request ? `Could you please arrange ${item_name}? 🌸` : `I sent you ${item_name} with love! ❤️`);

    const metadata = {
      item_name,
      item_price: Number(item_price) || 199,
      item_category: item_category || 'Care Essential',
      item_image: item_image || 'https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80',
      status: is_request ? 'REQUESTED' : 'SENT'
    };

    let targetReceiverId = receiver_id;
    let targetConnId = connection_id;

    // Resolve connection & receiver if not provided or demo id
    if (!targetConnId || targetConnId === 'demo-circle-connection') {
      if (targetReceiverId) {
        const { data: conn } = await supabase
          .from('partner_connections')
          .select('id')
          .or(`and(requester_id.eq.${senderId},recipient_id.eq.${targetReceiverId}),and(requester_id.eq.${targetReceiverId},recipient_id.eq.${senderId})`)
          .single();
        if (conn) {
          targetConnId = conn.id;
        } else {
          const { data: newConn } = await supabase
            .from('partner_connections')
            .insert([{
              requester_id: senderId,
              recipient_id: targetReceiverId,
              relationship: 'Other',
              custom_relationship_label: 'Partner',
              status: 'ACCEPTED'
            }])
            .select('id')
            .single();
          if (newConn) targetConnId = newConn.id;
        }
      } else {
        // Fallback to primary connection
        const { data: pConn } = await supabase
          .from('partner_connections')
          .select('id, requester_id, recipient_id')
          .or(`requester_id.eq.${senderId},recipient_id.eq.${senderId}`)
          .eq('status', 'ACCEPTED')
          .order('updated_at', { ascending: false })
          .limit(1)
          .single();
        if (pConn) {
          targetConnId = pConn.id;
          targetReceiverId = pConn.requester_id === senderId ? pConn.recipient_id : pConn.requester_id;
        }
      }
    } else if (!targetReceiverId) {
      const { data: conn } = await supabase.from('partner_connections').select('requester_id, recipient_id').eq('id', targetConnId).single();
      if (conn) {
        targetReceiverId = conn.requester_id === senderId ? conn.recipient_id : conn.requester_id;
      }
    }

    if (!targetReceiverId) {
      return res.status(400).json({ success: false, message: 'Could not determine receiver for care item' });
    }

    const { data: newMsg, error } = await supabase
      .from('circle_messages')
      .insert([{
        connection_id: targetConnId,
        sender_id: senderId,
        receiver_id: targetReceiverId,
        message_type: messageType,
        content,
        metadata
      }])
      .select()
      .single();

    if (error) throw error;

    // Send high-priority notification to recipient
    try {
      await supabase.from('notifications').insert([{
        user_id: targetReceiverId,
        title: is_request ? 'Care Item Requested 🌸' : 'Care Package Sent To You! 🎁',
        body: content,
        type: is_request ? 'CARE_REQUEST' : 'CARE_ITEM_SENT'
      }]);
    } catch (_) {}

    res.status(201).json({
      success: true,
      message: { ...newMsg, is_mine: true }
    });
  } catch (err) {
    next(err);
  }
};

// 5. Set custom relationship tag for a contact (e.g. Husband, Wife, Sister, Best Friend)
const setContactTag = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const { target_user_id, tag } = req.body;

    if (!target_user_id || !tag || !tag.trim()) {
      return res.status(400).json({ success: false, message: 'target_user_id and tag are required' });
    }

    const cleanTag = tag.trim();

    // Check existing partner connection
    const { data: conn } = await supabase
      .from('partner_connections')
      .select('id')
      .or(`and(requester_id.eq.${userId},recipient_id.eq.${target_user_id}),and(requester_id.eq.${target_user_id},recipient_id.eq.${userId})`)
      .single();

    if (conn) {
      const { error } = await supabase
        .from('partner_connections')
        .update({
          relationship: 'Other',
          custom_relationship_label: cleanTag,
          updated_at: new Date()
        })
        .eq('id', conn.id);
      if (error) throw error;
    } else {
      const { error } = await supabase
        .from('partner_connections')
        .insert([{
          requester_id: userId,
          recipient_id: target_user_id,
          relationship: 'Other',
          custom_relationship_label: cleanTag,
          status: 'ACCEPTED'
        }]);
      if (error) throw error;
    }

    res.json({ success: true, message: `Tag set to "${cleanTag}"`, tag: cleanTag });
  } catch (err) {
    next(err);
  }
};

// 6. Search Users by name or email
const searchUsers = async (req, res, next) => {
  try {
    const { query } = req.query;
    const currentUserId = req.user.id;

    if (!query || query.trim().length === 0) {
      return res.json({ success: true, users: [] });
    }

    const { data: users, error } = await supabase
      .from('users')
      .select('id, email, role, profiles(display_name, profile_image)')
      .or(`email.ilike.%${query.trim()}%,profiles.display_name.ilike.%${query.trim()}%`)
      .neq('id', currentUserId)
      .limit(20);

    if (error) throw error;

    // Fetch following status for each user
    const { data: follows } = await supabase
      .from('user_follows')
      .select('following_id')
      .eq('follower_id', currentUserId);

    const followingSet = new Set((follows || []).map(f => f.following_id));

    const mapped = (users || []).map(u => {
      const prof = Array.isArray(u.profiles) ? u.profiles[0] : u.profiles;
      return {
        id: u.id,
        email: u.email,
        display_name: prof?.display_name || u.email.split('@')[0],
        profile_image: prof?.profile_image || null,
        is_following: followingSet.has(u.id)
      };
    });

    res.json({ success: true, users: mapped });
  } catch (err) {
    next(err);
  }
};

// 7. Follow User
const followUser = async (req, res, next) => {
  try {
    const followerId = req.user.id;
    const { target_user_id } = req.body;

    if (!target_user_id) {
      return res.status(400).json({ success: false, message: 'target_user_id required' });
    }
    if (followerId === target_user_id) {
      return res.status(400).json({ success: false, message: 'Cannot follow yourself' });
    }

    const { error } = await supabase
      .from('user_follows')
      .upsert([{ follower_id: followerId, following_id: target_user_id }], { onConflict: 'follower_id, following_id' });

    if (error) throw error;

    res.json({ success: true, message: 'User followed successfully', is_following: true });
  } catch (err) {
    next(err);
  }
};

// 8. Unfollow User
const unfollowUser = async (req, res, next) => {
  try {
    const followerId = req.user.id;
    const { target_user_id } = req.body;

    if (!target_user_id) {
      return res.status(400).json({ success: false, message: 'target_user_id required' });
    }

    const { error } = await supabase
      .from('user_follows')
      .delete()
      .eq('follower_id', followerId)
      .eq('following_id', target_user_id);

    if (error) throw error;

    res.json({ success: true, message: 'User unfollowed successfully', is_following: false });
  } catch (err) {
    next(err);
  }
};

// 9. Get User Social Stats (Followers / Following counts)
const getUserSocialStats = async (req, res, next) => {
  try {
    const targetUserId = req.params.userId || req.user.id;

    const { count: followersCount } = await supabase
      .from('user_follows')
      .select('*', { count: 'exact', head: true })
      .eq('following_id', targetUserId);

    const { count: followingCount } = await supabase
      .from('user_follows')
      .select('*', { count: 'exact', head: true })
      .eq('follower_id', targetUserId);

    res.json({
      success: true,
      stats: {
        followers: followersCount || 0,
        following: followingCount || 0
      }
    });
  } catch (err) {
    next(err);
  }
};

// 10. Get Followers List
const getFollowers = async (req, res, next) => {
  try {
    const targetUserId = req.params.userId || req.user.id;
    const currentUserId = req.user.id;

    const { data: rows, error } = await supabase
      .from('user_follows')
      .select('follower_id, created_at, follower:follower_id(id, email, cyclecare_id, role, profiles(display_name, avatar_url))')
      .eq('following_id', targetUserId)
      .order('created_at', { ascending: false });

    if (error) throw error;

    // Check which ones the current user is following back
    const followerIds = (rows || []).map(r => r.follower_id);
    let myFollowings = new Set();
    if (followerIds.length > 0) {
      const { data: followingRows } = await supabase
        .from('user_follows')
        .select('following_id')
        .eq('follower_id', currentUserId)
        .in('following_id', followerIds);
      if (followingRows) {
        myFollowings = new Set(followingRows.map(f => f.following_id));
      }
    }

    const followers = (rows || []).map(r => {
      const u = r.follower || {};
      const prof = Array.isArray(u.profiles) ? u.profiles[0] : u.profiles;
      const name = prof?.display_name || u.email?.split('@')[0] || 'User';
      return {
        id: u.id || r.follower_id,
        display_name: name,
        email: u.email || '',
        avatar_url: prof?.avatar_url || null,
        is_following: myFollowings.has(r.follower_id),
        is_me: r.follower_id === currentUserId,
        followed_at: r.created_at
      };
    });

    res.json({ success: true, count: followers.length, users: followers });
  } catch (err) {
    next(err);
  }
};

// 11. Get Following List
const getFollowing = async (req, res, next) => {
  try {
    const targetUserId = req.params.userId || req.user.id;
    const currentUserId = req.user.id;

    const { data: rows, error } = await supabase
      .from('user_follows')
      .select('following_id, created_at, following:following_id(id, email, cyclecare_id, role, profiles(display_name, avatar_url))')
      .eq('follower_id', targetUserId)
      .order('created_at', { ascending: false });

    if (error) throw error;

    const following = (rows || []).map(r => {
      const u = r.following || {};
      const prof = Array.isArray(u.profiles) ? u.profiles[0] : u.profiles;
      const name = prof?.display_name || u.email?.split('@')[0] || 'User';
      return {
        id: u.id || r.following_id,
        display_name: name,
        email: u.email || '',
        avatar_url: prof?.avatar_url || null,
        is_following: true,
        is_me: r.following_id === currentUserId,
        followed_at: r.created_at
      };
    });

    res.json({ success: true, count: following.length, users: following });
  } catch (err) {
    next(err);
  }
};

module.exports = {
  getContacts,
  getMessages,
  sendMessage,
  sendCareItem,
  getQuickCareItems,
  searchUsers,
  followUser,
  unfollowUser,
  getUserSocialStats,
  getFollowers,
  getFollowing,
  setContactTag
};
