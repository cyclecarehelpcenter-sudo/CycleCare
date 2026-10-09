const supabase = require('../config/supabase');

const getDashboardStats = async (req, res, next) => {
  try {
    const { count: totalUsers } = await supabase.from('users').select('*', { count: 'exact', head: true });
    const { count: totalOrders } = await supabase.from('orders').select('*', { count: 'exact', head: true });
    const { data: paidOrders } = await supabase.from('orders').select('total_amount').eq('status', 'PAID');
    const { data: lowStockProducts } = await supabase.from('products').select('*').lte('stock', 5);

    // Count Real vs Demo accounts
    const { data: allUsers } = await supabase.from('users').select('email');
    const demoEmails = ['admin@cyclecare.app', 'demo@cyclecare.com', 'husband.demo@cyclecare.app', 'delivery.demo@cyclecare.app', 'newuser@cyclecare.com'];
    let demoCount = 0;
    let realCount = 0;
    (allUsers || []).forEach(u => {
      if (demoEmails.includes(u.email) || u.email.includes('demo') || u.email.endsWith('@cyclecare.com') || u.email.endsWith('@cyclecare.app')) {
        demoCount++;
      } else {
        realCount++;
      }
    });

    // Partner Connections Aggregate Metrics (Privacy-Safe: Zero Health Data)
    const { count: totalPartnerConnections } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true });
    const { count: activePartnerConnections } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true }).eq('status', 'ACCEPTED');
    const { count: pendingPartnerRequests } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');
    const { count: totalCircleMessages } = await supabase.from('circle_messages').select('*', { count: 'exact', head: true });

    const totalRevenue = (paidOrders || []).reduce((acc, curr) => acc + Number(curr.total_amount), 0);

    res.json({
      success: true,
      stats: {
        totalUsers: totalUsers || 0,
        realUsersCount: realCount,
        demoUsersCount: demoCount,
        totalOrders: totalOrders || 0,
        totalRevenue,
        lowStockAlertsCount: lowStockProducts ? lowStockProducts.length : 0,
        totalCircleMessages: totalCircleMessages || 0,
        partnerConnections: {
          total: totalPartnerConnections || 0,
          active: activePartnerConnections || 0,
          pending: pendingPartnerRequests || 0
        }
      },
      lowStockProducts: lowStockProducts || []
    });
  } catch (err) {
    next(err);
  }
};

const getUsers = async (req, res, next) => {
  try {
    const { data, error } = await supabase
      .from('users')
      .select('id, email, role, status, usage_mode, created_at, profiles(display_name, account_tag, emergency_contact_phone)')
      .order('created_at', { ascending: false });

    if (error) throw error;

    const demoEmails = ['admin@cyclecare.app', 'demo@cyclecare.com', 'husband.demo@cyclecare.app', 'delivery.demo@cyclecare.app', 'newuser@cyclecare.com'];
    const tagged = (data || []).map(u => {
      const prof = Array.isArray(u.profiles) ? u.profiles[0] : u.profiles;
      const isDemo = demoEmails.includes(u.email) || u.email.includes('demo') || u.email.endsWith('@cyclecare.com') || u.email.endsWith('@cyclecare.app');
      let accountTag = prof?.account_tag || (isDemo ? 'DEMO USER' : 'REAL USER');
      if (u.email === 'admin@cyclecare.app') accountTag = prof?.account_tag || 'DEMO ADMIN';
      else if (u.email === 'demo@cyclecare.com') accountTag = prof?.account_tag || 'DEMO GIRL';
      else if (u.email === 'husband.demo@cyclecare.app') accountTag = prof?.account_tag || 'DEMO HUSBAND';
      else if (u.email === 'delivery.demo@cyclecare.app') accountTag = prof?.account_tag || 'DEMO COURIER';

      return {
        ...u,
        account_type: isDemo ? 'DEMO' : 'REAL',
        account_tag: accountTag,
        display_name: prof?.display_name || u.email.split('@')[0],
        emergency_phone: prof?.emergency_contact_phone || 'None'
      };
    });

    res.json({ success: true, users: tagged });
  } catch (err) {
    next(err);
  }
};

const updateUserTag = async (req, res, next) => {
  try {
    const userId = req.params.id;
    const { tag, display_name, role, status } = req.body;

    if (display_name !== undefined || tag !== undefined) {
      const pUpdates = { updated_at: new Date() };
      if (display_name) pUpdates.display_name = display_name.trim();
      if (tag !== undefined) pUpdates.account_tag = tag.trim();
      await supabase.from('profiles').update(pUpdates).eq('user_id', userId);
    }

    if (role || status) {
      const uUpdates = { updated_at: new Date() };
      if (role) uUpdates.role = role;
      if (status) uUpdates.status = status;
      await supabase.from('users').update(uUpdates).eq('id', userId);
    }

    res.json({ success: true, message: 'User updated successfully' });
  } catch (err) {
    next(err);
  }
};

const getUserCycleLogs = async (req, res, next) => {
  try {
    const userId = req.params.id;
    const { data: periodLogs } = await supabase.from('period_logs').select('*').eq('user_id', userId).order('start_date', { ascending: false });
    const { data: symptomLogs } = await supabase.from('user_symptoms').select('*, symptoms(name)').eq('user_id', userId).order('logged_at', { ascending: false }).limit(20);
    const { data: moodLogs } = await supabase.from('user_moods').select('*, moods(name)').eq('user_id', userId).order('logged_at', { ascending: false }).limit(20);

    res.json({
      success: true,
      periodLogs: periodLogs || [],
      symptomLogs: symptomLogs || [],
      moodLogs: moodLogs || []
    });
  } catch (err) {
    next(err);
  }
};

const updateUserStatus = async (req, res, next) => {
  try {
    const userId = req.params.id;
    const { status } = req.body;

    if (!['ACTIVE', 'DISABLED'].includes(status)) {
      return res.status(400).json({ success: false, message: 'Invalid status' });
    }

    const { data, error } = await supabase
      .from('users')
      .update({ status, updated_at: new Date() })
      .eq('id', userId)
      .select()
      .single();

    if (error) throw error;

    // Log Audit
    await supabase.from('audit_logs').insert([{
      actor_user_id: req.user.id,
      action: 'UPDATE_USER_STATUS',
      entity_type: 'USER',
      entity_id: userId,
      metadata: { new_status: status }
    }]);

    res.json({ success: true, message: 'User status updated', user: data });
  } catch (err) {
    next(err);
  }
};

const getAdminProducts = async (req, res, next) => {
  try {
    const { data, error } = await supabase.from('products').select('*, categories(name)').order('created_at', { ascending: false });
    if (error) throw error;
    res.json({ success: true, products: data });
  } catch (err) {
    next(err);
  }
};

const createProduct = async (req, res, next) => {
  try {
    const { category_id, name, description, price, discount_price, stock, sku } = req.body;

    if (!name || !price || !sku) {
      return res.status(400).json({ success: false, message: 'Name, price, and SKU are required' });
    }

    const { data, error } = await supabase
      .from('products')
      .insert([{ category_id, name, description, price, discount_price, stock: stock || 0, sku, is_active: true }])
      .select()
      .single();

    if (error) throw error;

    await supabase.from('audit_logs').insert([{
      actor_user_id: req.user.id,
      action: 'CREATE_PRODUCT',
      entity_type: 'PRODUCT',
      entity_id: data.id,
      metadata: { sku, price }
    }]);

    // Record Store Notification in Database
    try {
      await supabase.from('notifications').insert([{
        title: 'New Store Arrival! 🛍️',
        body: `${name} is now available in CycleCare Store at ₹${discount_price || price}!`,
        type: 'STORE_PRODUCT_ADDED'
      }]);
    } catch (_) {}

    res.status(201).json({ success: true, message: 'Product created successfully', product: data });
  } catch (err) {
    next(err);
  }
};

const updateProduct = async (req, res, next) => {
  try {
    const productId = req.params.id;
    const { category_id, name, description, price, discount_price, stock, is_active } = req.body;

    const { data, error } = await supabase
      .from('products')
      .update({ category_id, name, description, price, discount_price, stock, is_active, updated_at: new Date() })
      .eq('id', productId)
      .select()
      .single();

    if (error) throw error;

    await supabase.from('audit_logs').insert([{
      actor_user_id: req.user.id,
      action: 'UPDATE_PRODUCT',
      entity_type: 'PRODUCT',
      entity_id: productId,
      metadata: { stock, price }
    }]);

    res.json({ success: true, message: 'Product updated', product: data });
  } catch (err) {
    next(err);
  }
};

const deleteProduct = async (req, res, next) => {
  try {
    const productId = req.params.id;
    const { error } = await supabase.from('products').delete().eq('id', productId);
    if (error) throw error;
    res.json({ success: true, message: 'Product deleted successfully' });
  } catch (err) {
    next(err);
  }
};

const getAdminOrders = async (req, res, next) => {
  try {
    const { data, error } = await supabase.from('orders').select('*, order_items(*), users(email), addresses(*)').order('created_at', { ascending: false });
    if (error) throw error;
    res.json({ success: true, orders: data });
  } catch (err) {
    next(err);
  }
};

const updateOrderStatus = async (req, res, next) => {
  try {
    const orderId = req.params.id;
    const { status } = req.body;

    const validStatuses = ['PENDING', 'PAYMENT_PENDING', 'PAID', 'PROCESSING', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
    if (!validStatuses.includes(status)) {
      return res.status(400).json({ success: false, message: 'Invalid order status' });
    }

    const { data, error } = await supabase
      .from('orders')
      .update({ status, updated_at: new Date() })
      .eq('id', orderId)
      .select()
      .single();

    if (error) throw error;

    await supabase.from('audit_logs').insert([{
      actor_user_id: req.user.id,
      action: 'UPDATE_ORDER_STATUS',
      entity_type: 'ORDER',
      entity_id: orderId,
      metadata: { new_status: status }
    }]);

    res.json({ success: true, message: 'Order status updated', order: data });
  } catch (err) {
    next(err);
  }
};

const getAuditLogs = async (req, res, next) => {
  try {
    const { data, error } = await supabase.from('audit_logs').select('*, users(email)').order('created_at', { ascending: false }).limit(100);
    if (error) throw error;
    res.json({ success: true, auditLogs: data });
  } catch (err) {
    next(err);
  }
};

const getAdminDeliveries = async (req, res, next) => {
  try {
    const { data: deliveries, error } = await supabase
      .from('deliveries')
      .select('*, orders(*), delivery_agents(*), delivery_live_locations(*)')
      .order('created_at', { ascending: false });

    if (error) throw error;
    res.json({ success: true, deliveries: deliveries || [] });
  } catch (err) {
    next(err);
  }
};

module.exports = {
  getDashboardStats,
  getUsers,
  updateUserStatus,
  updateUserTag,
  getUserCycleLogs,
  getAdminProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  getAdminOrders,
  updateOrderStatus,
  getAuditLogs,
  getAdminDeliveries
};
