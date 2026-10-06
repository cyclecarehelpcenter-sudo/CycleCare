const supabase = require('../config/supabase');

const createOrder = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const { address, coupon_code } = req.body;

    if (!address || !address.address_line || !address.pincode) {
      return res.status(400).json({ success: false, message: 'Valid delivery address is required' });
    }

    // Save / fetch address
    const { data: savedAddress, error: addrError } = await supabase
      .from('addresses')
      .insert([{
        user_id: userId,
        name: address.name,
        phone: address.phone,
        address_line: address.address_line,
        city: address.city,
        state: address.state,
        pincode: address.pincode,
        type: address.type || 'HOME'
      }])
      .select()
      .single();

    if (addrError) throw addrError;

    // Fetch user cart
    const { data: userCart } = await supabase.from('cart').select('id').eq('user_id', userId).single();
    if (!userCart) return res.status(400).json({ success: false, message: 'Cart is empty' });

    const { data: cartItems } = await supabase
      .from('cart_items')
      .select('*, products(*)')
      .eq('cart_id', userCart.id);

    if (!cartItems || cartItems.length === 0) {
      return res.status(400).json({ success: false, message: 'Cart is empty' });
    }

    // Calculate subtotal authoritatively from current DB prices & verify stock
    let subtotal = 0;
    const orderItemSnapshots = [];

    for (const item of cartItems) {
      const product = item.products;
      if (!product || !product.is_active) {
        return res.status(400).json({ success: false, message: `Product ${product?.name || ''} is no longer available` });
      }
      if (product.stock < item.quantity) {
        return res.status(400).json({ success: false, message: `Stock insufficient for ${product.name}. Available: ${product.stock}` });
      }

      const unitPrice = Number(product.discount_price || product.price);
      const total = unitPrice * item.quantity;
      subtotal += total;

      orderItemSnapshots.push({
        product_id: product.id,
        product_name_snapshot: product.name,
        unit_price: unitPrice,
        quantity: item.quantity,
        total: total
      });
    }

    // Coupon calculation
    let discount = 0;
    let couponId = null;
    if (coupon_code) {
      const { data: coupon } = await supabase.from('coupons').select('*').eq('code', coupon_code.toUpperCase()).eq('is_active', true).single();
      if (coupon && subtotal >= Number(coupon.minimum_order || 0)) {
        couponId = coupon.id;
        if (coupon.discount_type === 'PERCENTAGE') {
          discount = (subtotal * Number(coupon.discount_value)) / 100;
          if (coupon.maximum_discount && discount > Number(coupon.maximum_discount)) {
            discount = Number(coupon.maximum_discount);
          }
        } else {
          discount = Number(coupon.discount_value);
        }
      }
    }

    const deliveryFee = subtotal > 499 ? 0 : 40;
    const totalAmount = Math.max(0, subtotal - discount + deliveryFee);

    // Create order header
    const { data: order, error: orderError } = await supabase
      .from('orders')
      .insert([{
        user_id: userId,
        address_id: savedAddress.id,
        subtotal: subtotal,
        discount: discount,
        delivery_fee: deliveryFee,
        total_amount: totalAmount,
        coupon_id: couponId,
        status: 'PENDING'
      }])
      .select()
      .single();

    if (orderError) throw orderError;

    // Create order items
    const itemsToInsert = orderItemSnapshots.map(i => ({ ...i, order_id: order.id }));
    await supabase.from('order_items').insert(itemsToInsert);

    // Clear cart
    await supabase.from('cart_items').delete().eq('cart_id', userCart.id);

    res.status(201).json({
      success: true,
      message: 'Order created successfully',
      order: {
        ...order,
        items: itemsToInsert
      }
    });
  } catch (err) {
    next(err);
  }
};

const getUserOrders = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const { data: orders, error } = await supabase
      .from('orders')
      .select('*, order_items(*), addresses(*), payments(*)')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error) throw error;
    res.json({ success: true, orders });
  } catch (err) {
    next(err);
  }
};

const getOrderById = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const orderId = req.params.id;

    const { data: order, error } = await supabase
      .from('orders')
      .select('*, order_items(*), addresses(*), payments(*)')
      .eq('id', orderId)
      .eq('user_id', userId)
      .single();

    if (error || !order) return res.status(404).json({ success: false, message: 'Order not found' });
    res.json({ success: true, order });
  } catch (err) {
    next(err);
  }
};

const cancelOrder = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const orderId = req.params.id;

    const { data: order } = await supabase.from('orders').select('*').eq('id', orderId).eq('user_id', userId).single();
    if (!order) return res.status(404).json({ success: false, message: 'Order not found' });

    if (['SHIPPED', 'DELIVERED', 'CANCELLED'].includes(order.status)) {
      return res.status(400).json({ success: false, message: `Cannot cancel order in ${order.status} state` });
    }

    const { data: updatedOrder, error } = await supabase
      .from('orders')
      .update({ status: 'CANCELLED', updated_at: new Date() })
      .eq('id', orderId)
      .select()
      .single();

    if (error) throw error;
    res.json({ success: true, message: 'Order cancelled successfully', order: updatedOrder });
  } catch (err) {
    next(err);
  }
};

// Re-order Previous Items (Buy Again)
const buyAgain = async (req, res, next) => {
  try {
    const userId = req.user.id;
    const orderId = req.params.id;

    const { data: previousOrder } = await supabase
      .from('orders')
      .select('*, order_items(*)')
      .eq('id', orderId)
      .eq('user_id', userId)
      .single();

    if (!previousOrder) return res.status(404).json({ success: false, message: 'Previous order not found' });

    let { data: userCart } = await supabase.from('cart').select('id').eq('user_id', userId).single();
    if (!userCart) {
      const { data: newCart } = await supabase.from('cart').insert([{ user_id: userId }]).select().single();
      userCart = newCart;
    }

    let itemsReadded = 0;
    for (const item of previousOrder.order_items) {
      if (!item.product_id) continue;

      // Revalidate product exists, active & in stock
      const { data: product } = await supabase.from('products').select('*').eq('id', item.product_id).single();
      if (product && product.is_active && product.stock > 0) {
        const qtyToAdd = Math.min(item.quantity, product.stock);
        const activePrice = product.discount_price || product.price;

        await supabase
          .from('cart_items')
          .upsert([{ cart_id: userCart.id, product_id: product.id, quantity: qtyToAdd, unit_price: activePrice }], { onConflict: 'cart_id, product_id' });

        itemsReadded++;
      }
    }

    res.json({
      success: true,
      message: `Re-added ${itemsReadded} available item(s) to your cart.`
    });
  } catch (err) {
    next(err);
  }
};

module.exports = {
  createOrder,
  getUserOrders,
  getOrderById,
  cancelOrder,
  buyAgain
};
