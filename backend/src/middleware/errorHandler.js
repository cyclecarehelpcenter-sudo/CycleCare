const errorHandler = (err, req, res, next) => {
  console.error('[Error Stack]', err.stack || err);

  const statusCode = res.statusCode !== 200 ? res.statusCode : 500;
  
  res.status(statusCode).json({
    success: false,
    message: err.message || 'Internal Server Error',
    code: err.code || 'SERVER_ERROR'
  });
};

module.exports = errorHandler;
