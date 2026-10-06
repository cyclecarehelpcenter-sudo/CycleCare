const express = require('express');
const router = express.Router();
const ordersController = require('../controllers/ordersController');
const authenticateToken = require('../middleware/auth');

router.use(authenticateToken);

router.post('/', ordersController.createOrder);
router.get('/', ordersController.getUserOrders);
router.get('/:id', ordersController.getOrderById);
router.post('/:id/cancel', ordersController.cancelOrder);
router.post('/:id/buy-again', ordersController.buyAgain);

module.exports = router;
