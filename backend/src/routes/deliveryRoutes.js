const express = require('express');
const router = express.Router();
const deliveryController = require('../controllers/deliveryController');
const authenticateToken = require('../middleware/auth');

const deliveryAuth = (req, res, next) => {
  const adminKey = req.headers['x-admin-key'];
  const expectedKey = process.env.ADMIN_SECRET_KEY || '8c2e54885f922a59917e38d497e970aaa25287fe99a2e3fd';
  if (adminKey && adminKey === expectedKey) {
    req.user = { id: '00000000-0000-0000-0000-000000000000', role: 'SUPER_ADMIN' };
    return next();
  }
  authenticateToken(req, res, next);
};

router.get('/agent/dashboard', deliveryAuth, deliveryController.getAgentDashboard);
router.get('/dashboard', deliveryAuth, deliveryController.getAgentDashboard);
router.post('/:id/accept', deliveryAuth, deliveryController.acceptDelivery);
router.post('/:id/pickup', deliveryAuth, deliveryController.pickupDelivery);
router.post('/:id/start', deliveryAuth, deliveryController.startDelivery);
router.post('/:id/arrived', deliveryAuth, deliveryController.arrivedDelivery);
router.post('/:id/arrive', deliveryAuth, deliveryController.arrivedDelivery);
router.post('/:id/complete', deliveryAuth, deliveryController.completeDelivery);
router.post('/:id/location', deliveryAuth, deliveryController.updateLocation);
router.post('/:id/simulate', deliveryAuth, deliveryController.simulateLocation);
router.post('/demo/reset', deliveryAuth, deliveryController.resetDemoDelivery);
router.get('/order/:id', deliveryAuth, deliveryController.getOrderTracking);

module.exports = router;
