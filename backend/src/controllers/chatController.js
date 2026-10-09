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

// 1. Get Circle Contacts (Husband, Wife, Parents, Partner, etc.)
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
    for (const c of (connections || [])) {
      const isRecipient = c.recipient_id === userId;
      const otherUser = isRecipient ? c.requester : c.recipient;
      if (!otherUser) continue;

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

      contacts.push({
        connection_id: c.id,
        user_id: otherUser.id,
        display_name: otherUser.profiles?.display_name || 'My Circle Partner',
        cyclecare_id: otherUser.cyclecare_id ? `@${otherUser.cyclecare_id}` : '@partner',
        relationship: c.relationship || 'Partner',
        last_message: latestMsg ? latestMsg.content : 'Start a caring conversation...',
        last_message_type: latestMsg ? latestMsg.message_type : 'TEXT',
        last_message_time: latestMsg ? latestMsg.created_at : c.created_at,
        unread_count: unreadCount || 0
      });
    }

    // If no connections exist yet, provide a mock/starter partner so user can immediately experience chat
    if (contacts.length === 0) {
      contacts.push({
        connection_id: "demo-circle-connection",
        user_id: "demo-partner-id",
        display_name: "Aman (Husband ❤️)",
        cyclecare_id: "@aman_care",
        relationship: "Husband",
        last_message: "Let me know if you need any pads or hot water bag today!",
        last_message_type: "TEXT",
        last_message_time: new Date().toISOString(),
        unread_count: 0
      });
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
    const { connection_id } = req.params;

    if (!connection_id) {
      return res.status(400).json({ success: false, message: 'connection_id is required' });
    }

    let messages = [];

    if (connection_id !== 'demo-circle-connection') {
      const { data, error } = await supabase
        .from('circle_messages')
        .select('*')
        .eq('connection_id', connection_id)
        .order('created_at', { ascending: true });

      if (error) throw error;
      messages = data || [];

      // Mark received messages as read
      await supabase
        .from('circle_messages')
        .update({ is_read: true })
        .eq('connection_id', connection_id)
        .eq('receiver_id', userId)
        .eq('is_read', false);
    } else {
      // Demo conversation seed
      messages = [
        {
          id: "msg-1",
          connection_id: "demo-circle-connection",
          sender_id: "demo-partner-id",
          receiver_id: userId,
          message_type: "TEXT",
          content: "Hey, how are you feeling today? Take rest and stay hydrated!",
          metadata: {},
          is_read: true,
          created_at: new Date(Date.now() - 3600000 * 2).toISOString()
        },
        {
          id: "msg-2",
          connection_id: "demo-circle-connection",
          sender_id: userId,
          receiver_id: "demo-partner-id",
          message_type: "CARE_REQUEST",
          content: "Having some cramps today. Can you please order a heat patch for me?",
          metadata: {
            item_name: "Instant Warmth Heat Patch (Pack of 3)",
            item_price: 199,
            item_category: "Comfort & Cramps",
            item_image: "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80",
            status: "ORDERED"
          },
          is_read: true,
          created_at: new Date(Date.now() - 3600000).toISOString()
        },
        {
          id: "msg-3",
          connection_id: "demo-circle-connection",
          sender_id: "demo-partner-id",
          receiver_id: userId,
          message_type: "CARE_ITEM_SENT",
          content: "Ordered it for you! Will reach your doorstep in 15 mins. Take care ❤️",
          metadata: {
            item_name: "CycleCare Emergency SOS Care Kit",
            item_price: 499,
            item_category: "Emergency & Medical",
            item_image: "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=600&q=80",
            status: "SENT"
          },
          is_read: true,
          created_at: new Date(Date.now() - 1800000).toISOString()
        }
      ];
    }

    res.json({
      success: true,
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
    const { connection_id, receiver_id, content, message_type = 'TEXT' } = req.body;

    if (!content || !content.trim()) {
      return res.status(400).json({ success: false, message: 'Message content cannot be empty' });
    }

    if (connection_id === 'demo-circle-connection') {
      const mockMsg = {
        id: `msg_${Date.now()}`,
        connection_id,
        sender_id: senderId,
        receiver_id: receiver_id || 'demo-partner-id',
        message_type,
        content: content.trim(),
        metadata: {},
        is_read: false,
        created_at: new Date().toISOString(),
        is_mine: true
      };
      return res.status(201).json({ success: true, message: mockMsg });
    }

    // Resolve receiver_id if not explicitly provided
    let targetReceiverId = receiver_id;
    if (!targetReceiverId && connection_id) {
      const { data: conn } = await supabase.from('partner_connections').select('requester_id, recipient_id').eq('id', connection_id).single();
      if (conn) {
        targetReceiverId = conn.requester_id === senderId ? conn.recipient_id : conn.requester_id;
      }
    }

    const { data: newMsg, error } = await supabase
      .from('circle_messages')
      .insert([{
        connection_id,
        sender_id: senderId,
        receiver_id: targetReceiverId,
        message_type,
        content: content.trim(),
        metadata: {}
      }])
      .select()
      .single();

    if (error) throw error;

    // Send push notification to partner
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
    const { 
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

    if (connection_id === 'demo-circle-connection') {
      const mockMsg = {
        id: `msg_item_${Date.now()}`,
        connection_id,
        sender_id: senderId,
        receiver_id: receiver_id || 'demo-partner-id',
        message_type: messageType,
        content,
        metadata,
        is_read: false,
        created_at: new Date().toISOString(),
        is_mine: true
      };
      return res.status(201).json({ success: true, message: mockMsg });
    }

    // Resolve receiver
    let targetReceiverId = receiver_id;
    if (!targetReceiverId && connection_id) {
      const { data: conn } = await supabase.from('partner_connections').select('requester_id, recipient_id').eq('id', connection_id).single();
      if (conn) {
        targetReceiverId = conn.requester_id === senderId ? conn.recipient_id : conn.requester_id;
      }
    }

    const { data: newMsg, error } = await supabase
      .from('circle_messages')
      .insert([{
        connection_id,
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

// 5. Get Quick Care Catalog
const getQuickCareItems = (req, res) => {
  res.json({
    success: true,
    items: QUICK_CARE_ITEMS
  });
};

module.exports = {
  getContacts,
  getMessages,
  sendMessage,
  sendCareItem,
  getQuickCareItems
};
