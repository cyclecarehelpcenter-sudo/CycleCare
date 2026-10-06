const supabase = require('../config/supabase');

const getDashboardStats = async (req, res, next) => {
  try {
    const { count: totalUsers } = await supabase.from('users').select('*', { count: 'exact', head: true });
    const { count: totalOrders } = await supabase.from('orders').select('*', { count: 'exact', head: true });
    const { data: paidOrders } = await supabase.from('orders').select('total_amount').eq('status', 'PAID');
    const { data: lowStockProducts } = await supabase.from('products').select('*').lte('stock', 5);

    // Partner Connections Aggregate Metrics (Privacy-Safe: Zero Health Data)
    const { count: totalPartnerConnections } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true });
    const { count: activePartnerConnections } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true }).eq('status', 'ACCEPTED');
    const { count: pendingPartnerRequests } = await supabase.from('partner_connections').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');

    const totalRevenue = (paidOrders || []).reduce((acc, curr) => acc + Number(curr.total_amount), 0);

    res.json({
      success: true,
      stats: {
        totalUsers: totalUsers || 0,
        totalOrders: totalOrders || 0,
        totalRevenue,
        lowStockAlertsCount: lowStockProducts ? lowStockProducts.length : 0,
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
      .select('id, email, role, status, created_at, profiles(display_name)')
      .order('created_at', { ascending: false });

    if (error) throw error;
    res.json({ success: true, users: data });
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

module.exports = {
  getDashboardStats,
  getUsers,
  updateUserStatus,
  getAdminProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  getAdminOrders,
  updateOrderStatus,
  getAuditLogs
};
